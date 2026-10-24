package net.colin.minegolf.dev;

import net.colin.minegolf.MineGolf;
import net.colin.minegolf.block.ModBlocks;
import net.colin.minegolf.course.Course;
import net.colin.minegolf.course.CourseSavedData;
import net.colin.minegolf.course.Hole;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.QuartPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Dev tool, only active in the development environment ({@code /minegolf_dev demo}): finds a fairly flat
 * spot near spawn and lays a 6-hole course into the natural terrain. Holes follow bent centre lines,
 * the play surface steps up and down in one-block terraces along the hole, and the rough blends back
 * into the landscape with wavy edges.
 */
public final class DemoCourseBuilder {
	private static DemoCourseBuilder current;

	public static void register() {
		if (!FabricLoader.getInstance().isDevelopmentEnvironment()) return;
		CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> dispatcher.register(
				Commands.literal("minegolf_dev").then(Commands.literal("demo").executes(ctx -> {
					MinecraftServer server = ctx.getSource().getServer();
					// A second call while building would replace the first builder before it welcomes the player
					if (current != null && current.server == server && current.stage != Stage.DONE) {
						ctx.getSource().sendFailure(Component.literal("A demo course is already being built, please wait."));
						return 0;
					}
					current = new DemoCourseBuilder(server);
					current.chat("Building a demo course: searching for a good spot near spawn...");
					return 1;
				}))));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (current != null && current.server == server) current.tick();
		});
	}

	// ---------------------------------------------------------------- layout

	static final String NAME = "MineGolf Links";
	static final int W = 290, D = 245, M = 20;

	/**
	 * A hazard along a hole: s along the centre line (from the tee, or from the cup when fromGreen),
	 * lateral offset (+ = right of the playing direction).
	 */
	record Feature(String type, double s, double lateral, int rx, int rz, boolean fromGreen) {
	}

	private static Feature at(String type, double s, double lateral, int rx, int rz) {
		return new Feature(type, s, lateral, rx, rz, false);
	}

	private static Feature green(String type, double s, double lateral, int rx, int rz) {
		return new Feature(type, s, lateral, rx, rz, true);
	}

	record HoleDef(int n, int par, String name, int[][] points, double[][] shape, double teeLift, Feature... features) {
	}

	static final HoleDef[] HOLES = {
			new HoleDef(1, 3, "Opener", new int[][] {{25, 32}, {85, 26}},
					new double[][] {{0, 0}, {0.45, 0}, {0.6, 1}, {1, 1}}, 0,
					green("bunker", -10, -4, 3, 2), green("bunker", 5, 6, 2, 2)),
			new HoleDef(2, 4, "Dogleg Pond", new int[][] {{115, 30}, {200, 30}, {225, 70}},
					new double[][] {{0, 0}, {0.55, 0}, {0.7, -1}, {0.85, -2}, {1, -2}}, 0,
					at("pond", 74, 13, 6, 5), at("bunker", 52, -7, 3, 2),
					green("bunker", 0, 9, 3, 2), green("bunker", -12, -7, 2, 2)),
			new HoleDef(3, 3, "Island", new int[][] {{255, 95}, {262, 135}},
					new double[][] {{0, 0}, {1, 0}}, 1,
					green("island", 0, 0, 0, 0)),
			new HoleDef(4, 5, "The Long One", new int[][] {{240, 170}, {150, 168}, {92, 122}, {42, 120}},
					new double[][] {{0, 0}, {0.25, 1}, {0.4, 2}, {0.55, 1}, {0.7, 0}, {1, -1}}, 0,
					at("creek", 58, 0, 2, 11), at("bunker", 88, 8, 3, 2), at("bunker", 128, -8, 3, 2),
					green("bunker", -9, 0, 2, 2), green("bunker", 3, -8, 3, 2)),
			new HoleDef(5, 4, "Lookout", new int[][] {{30, 180}, {82, 212}, {150, 210}},
					new double[][] {{0, 0}, {0.35, -1}, {1, -1}}, 4,
					at("bunker", 58, -8, 3, 2), at("bunker", 70, 7, 2, 2), green("bunker", 8, 0, 3, 2)),
			new HoleDef(6, 3, "Home", new int[][] {{182, 214}, {242, 212}},
					new double[][] {{0, 0}, {0.5, 1}, {1, 1}}, 0,
					green("bunker", -10, 0, 2, 2), green("bunker", 0, 8, 3, 2),
					green("bunker", 9, 0, 3, 2), green("pond", -3, -12, 5, 3)),
	};

	/** A hole's bent centre line in world coordinates, with its terrace profile. */
	private final class Lane {
		final HoleDef def;
		final double[][] p;
		final double[] cum;
		final double length;
		final double phase;
		int[] profile; // play level per s, index s + PAD
		int teeLevel, greenLevel;
		static final int PAD = 20;

		Lane(HoleDef def) {
			this.def = def;
			p = new double[def.points.length][];
			for (int i = 0; i < p.length; i++) p[i] = new double[] {ox + def.points[i][0], oz + def.points[i][1]};
			cum = new double[p.length];
			for (int i = 1; i < p.length; i++) cum[i] = cum[i - 1] + Math.hypot(p[i][0] - p[i - 1][0], p[i][1] - p[i - 1][1]);
			length = cum[p.length - 1];
			phase = def.n * 1.7;
		}

		/** {s, distance, lateral sign} of the closest point; the line extends straight past both ends. */
		double[] project(double x, double z) {
			double best = Double.MAX_VALUE, bestS = 0, side = 1;
			for (int i = 0; i + 1 < p.length; i++) {
				double ax = p[i][0], az = p[i][1], dx = p[i + 1][0] - ax, dz = p[i + 1][1] - az;
				double segLen = Math.hypot(dx, dz);
				double t = ((x - ax) * dx + (z - az) * dz) / (segLen * segLen);
				double lo = i == 0 ? Double.NEGATIVE_INFINITY : 0, hi = i == p.length - 2 ? Double.POSITIVE_INFINITY : 1;
				t = Math.clamp(t, lo, hi);
				double px = ax + dx * t, pz = az + dz * t;
				double dist = Math.hypot(x - px, z - pz);
				if (dist < best) {
					best = dist;
					bestS = cum[i] + t * segLen;
					side = Math.signum(dx * (z - az) - dz * (x - ax));
				}
			}
			return new double[] {bestS, best, side};
		}

		double[] pointAt(double s, double lateral) {
			int i = 0;
			while (i + 2 < p.length && s > cum[i + 1]) i++;
			double dx = p[i + 1][0] - p[i][0], dz = p[i + 1][1] - p[i][1];
			double segLen = Math.hypot(dx, dz);
			double t = (s - cum[i]) / segLen;
			// right of the direction (dx, dz) is (-dz, dx) in Minecraft's x/z
			return new double[] {p[i][0] + dx * t - dz / segLen * lateral, p[i][1] + dz * t + dx / segLen * lateral};
		}

		double corridor(double s) {
			return 15 + 2.5 * Math.sin(s / 11 + phase);
		}

		double playHalf(double s) {
			return 9 + 1.5 * Math.sin(s / 8 + phase * 2);
		}

		double fairwayHalf(double s) {
			return 5 + 1.3 * Math.sin(s / 7 + phase * 3);
		}

		int level(double s) {
			int i = (int) Math.round(s) + PAD;
			return profile[Math.clamp(i, 0, profile.length - 1)];
		}
	}

	// ---------------------------------------------------------------- state

	/** Each stage runs over several ticks so the game stays responsive and can show progress. */
	private enum Stage {SEARCH, LOADING, BUILD, WELCOME, DONE}

	/** Milliseconds of site search per tick. */
	private static final long SEARCH_BUDGET_MS = 30;
	private static final int SEARCH_RANGE = 640, SEARCH_STEP = 64;
	/** Spacing of the height samples inside one candidate spot; each sample costs a noise evaluation. */
	private static final int SAMPLE_STEP = 28;

	private record Candidate(int x, int z, double score) {
	}

	private final MinecraftServer server;
	private final ServerLevel level;
	private final CommandSourceStack source;
	private Stage stage = Stage.SEARCH;
	private final List<int[]> offsets = new ArrayList<>();
	private final List<Candidate> candidates = new ArrayList<>();
	private int lastPercent = -1;
	private List<long[]> forced = List.of();
	private final List<String> welcome = new ArrayList<>();
	private final List<String> log = new ArrayList<>();
	private final List<Lane> lanes = new ArrayList<>();
	private int ox, oz;
	private int[][] ground; // natural ground height over the footprint + margin

	DemoCourseBuilder(MinecraftServer server) {
		this.server = server;
		this.level = server.overworld();
		this.source = server.createCommandSourceStack().withSuppressedOutput();
		for (int cx = -SEARCH_RANGE; cx <= SEARCH_RANGE; cx += SEARCH_STEP) {
			for (int cz = -SEARCH_RANGE; cz <= SEARCH_RANGE; cz += SEARCH_STEP) offsets.add(new int[] {cx, cz});
		}
	}

	private void tick() {
		switch (stage) {
			case SEARCH -> {
				long until = System.currentTimeMillis() + SEARCH_BUDGET_MS;
				while (candidates.size() < offsets.size() && System.currentTimeMillis() < until) {
					int[] o = offsets.get(candidates.size());
					candidates.add(score(o[0], o[1]));
				}
				progress("Searching for a spot", candidates.size(), offsets.size());
				if (candidates.size() < offsets.size()) return;
				pickSite();
				forced = chunks();
				for (long[] c : forced) level.setChunkForced((int) c[0], (int) c[1], true);
				MineGolf.LOGGER.info("DEMO site {},{} - loading {} chunks", ox, oz, forced.size());
				chat(String.format("Spot found at %d, %d. Loading the terrain (%d chunks)...", ox + W / 2, oz + D / 2, forced.size()));
				lastPercent = -1;
				stage = Stage.LOADING;
			}
			case LOADING -> {
				int loaded = 0;
				for (long[] c : forced) {
					if (level.getChunkSource().hasChunk((int) c[0], (int) c[1])) loaded++;
				}
				progress("Loading terrain", loaded, forced.size());
				if (loaded < forced.size()) return;
				// Building takes a few seconds in one go; say so a tick earlier so the message gets out first
				chat("Shaping the land and building 6 holes, the game may pause for a few seconds...");
				stage = Stage.BUILD;
			}
			case BUILD -> {
				build();
				try {
					renderPreview(server.getWorldPath(LevelResource.ROOT).resolve("minegolf_preview.png"));
				} catch (IOException e) {
					MineGolf.LOGGER.error("DEMO preview failed", e);
				}
				for (long[] c : forced) level.setChunkForced((int) c[0], (int) c[1], false);
				MineGolf.LOGGER.info("DEMO BUILD done\n{}", String.join("\n", log));
				stage = Stage.WELCOME;
			}
			case WELCOME -> {
				if (server.getPlayerList().getPlayers().isEmpty()) return;
				welcome.forEach(this::cmd);
				stage = Stage.DONE;
			}
			case DONE -> {
			}
		}
	}

	/** Chat line to everyone. */
	private void chat(String text) {
		server.getPlayerList().broadcastSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY), false);
	}

	/** Progress in the action bar, sent when the percentage changes. */
	private void progress(String what, int done, int total) {
		int percent = total == 0 ? 100 : done * 100 / total;
		if (percent == lastPercent) return;
		lastPercent = percent;
		Component bar = Component.literal(what + "... " + percent + "%").withStyle(ChatFormatting.YELLOW);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) player.sendOverlayMessage(bar);
	}

	// ---------------------------------------------------------------- site search

	/** Lower is better: some relief is welcome, cliffs, water and distance from spawn are not. */
	private Candidate score(int cx, int cz) {
		ChunkGenerator gen = level.getChunkSource().getGenerator();
		RandomState rs = level.getChunkSource().randomState();
		BlockPos spawn = level.getRespawnData().globalPos().pos();
		int x0 = spawn.getX() + cx - W / 2, z0 = spawn.getZ() + cz - D / 2;
		double sum = 0, sq = 0;
		int n = 0, wet = 0;
		for (int x = x0 + 10; x < x0 + W - 10; x += SAMPLE_STEP) {
			for (int z = z0 + 10; z < z0 + D - 10; z += SAMPLE_STEP) {
				int top = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, rs);
				int floor = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
				if (floor < top) wet++;
				sum += top;
				sq += (double) top * top;
				n++;
			}
		}
		double mean = sum / n;
		double std = Math.sqrt(Math.max(0, sq / n - mean * mean));
		double score = Math.abs(std - 2) * 3 + wet * 2 + (mean < 64 ? 40 : 0) + (mean > 110 ? 40 : 0)
				+ Math.hypot(cx, cz) / 64.0;
		return new Candidate(x0, z0, score);
	}

	/** Best scored spot in a pleasant biome; the biome comes from the generator, without generating chunks. */
	private void pickSite() {
		List<Candidate> list = new ArrayList<>(candidates);
		list.sort((a, b) -> Double.compare(a.score, b.score));
		for (Candidate c : list.subList(0, Math.min(15, list.size()))) {
			int mx = c.x + W / 2, mz = c.z + D / 2;
			var biome = level.getUncachedNoiseBiome(QuartPos.fromBlock(mx), QuartPos.fromBlock(80), QuartPos.fromBlock(mz));
			boolean nice = biome.is(Biomes.PLAINS) || biome.is(Biomes.SUNFLOWER_PLAINS) || biome.is(Biomes.MEADOW)
					|| biome.is(Biomes.FOREST) || biome.is(Biomes.FLOWER_FOREST) || biome.is(Biomes.BIRCH_FOREST)
					|| biome.is(Biomes.CHERRY_GROVE);
			log.add("candidate " + c.x + "," + c.z + " score=" + String.format("%.1f", c.score) + " biome=" + biome.getRegisteredName());
			if (nice) {
				ox = c.x;
				oz = c.z;
				log.add("site " + ox + "," + oz + " biome=" + biome.getRegisteredName());
				return;
			}
		}
		ox = list.getFirst().x;
		oz = list.getFirst().z;
		log.add("site (fallback) " + ox + "," + oz);
	}

	private List<long[]> chunks() {
		List<long[]> out = new ArrayList<>();
		for (int cx = (ox - M) >> 4; cx <= (ox + W + M) >> 4; cx++) {
			for (int cz = (oz - M) >> 4; cz <= (oz + D + M) >> 4; cz++) out.add(new long[] {cx, cz});
		}
		return out;
	}

	// ---------------------------------------------------------------- terrain

	private static boolean isGround(BlockState s) {
		if (s.isAir() || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(BlockTags.REPLACEABLE)
				|| s.is(BlockTags.FLOWERS) || s.is(Blocks.SNOW) || s.is(Blocks.BAMBOO) || s.is(Blocks.SUGAR_CANE)
				|| s.is(Blocks.PUMPKIN) || s.is(Blocks.MELON) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.MOSS_CARPET)
				|| s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.is(Blocks.RED_MUSHROOM_BLOCK) || s.is(Blocks.MUSHROOM_STEM)) {
			return false;
		}
		return s.isSolid() || !s.getFluidState().isEmpty();
	}

	private void readGround() {
		ground = new int[W + 2 * M][D + 2 * M];
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int i = 0; i < W + 2 * M; i++) {
			for (int j = 0; j < D + 2 * M; j++) {
				int x = ox - M + i, z = oz - M + j;
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				while (y > level.getMinY() && !isGround(level.getBlockState(p.set(x, y, z)))) y--;
				ground[i][j] = y;
			}
		}
	}

	private int g(int x, int z) {
		return ground[Math.clamp(x - ox + M, 0, W + 2 * M - 1)][Math.clamp(z - oz + M, 0, D + 2 * M - 1)];
	}

	private double smooth(double x, double z, int r) {
		double sum = 0;
		int n = 0;
		for (int a = -r; a <= r; a += 2) {
			for (int b = -r; b <= r; b += 2) {
				sum += g((int) Math.round(x) + a, (int) Math.round(z) + b);
				n++;
			}
		}
		return sum / n;
	}

	private static int median(List<Integer> values) {
		values.sort(Integer::compare);
		return values.get(values.size() / 2);
	}

	private static double interpolate(double[][] shape, double f) {
		for (int i = 0; i + 1 < shape.length; i++) {
			if (f <= shape[i + 1][0]) {
				double t = (f - shape[i][0]) / Math.max(1e-9, shape[i + 1][0] - shape[i][0]);
				return shape[i][1] + (shape[i + 1][1] - shape[i][1]) * Math.clamp(t, 0, 1);
			}
		}
		return shape[shape.length - 1][1];
	}

	/** Play level along the hole: terrain plus the hole's designed rise and fall, in one-block terraces. */
	private void profile(Lane lane) {
		int n = (int) Math.ceil(lane.length) + 2 * Lane.PAD;
		double[] raw = new double[n];
		for (int i = 0; i < n; i++) {
			double s = Math.clamp(i - Lane.PAD, 0, lane.length);
			double[] c = lane.pointAt(s, 0);
			raw[i] = smooth(c[0], c[1], 3);
		}
		List<Integer> all = new ArrayList<>();
		for (double r : raw) all.add((int) Math.round(r));
		int base = median(all);
		double[] wanted = new double[n];
		for (int i = 0; i < n; i++) {
			double sum = 0;
			int cnt = 0;
			for (int k = Math.max(0, i - 12); k <= Math.min(n - 1, i + 12); k++) {
				sum += raw[k];
				cnt++;
			}
			double s = Math.clamp(i - Lane.PAD, 0, lane.length);
			wanted[i] = Math.clamp(sum / cnt, base - 2, base + 2) + interpolate(lane.def.shape, s / lane.length);
		}
		int[] prof = new int[n];
		int cur = (int) Math.round(wanted[Lane.PAD + 6]);
		int lastChange = -100;
		for (int i = 0; i < n; i++) {
			double s = i - Lane.PAD;
			int want = (int) Math.round(wanted[i]);
			boolean onGreen = s >= lane.length - 9;
			if (!onGreen && s > 6 && want != cur && i - lastChange >= 8) {
				cur += Integer.signum(want - cur);
				lastChange = i;
			}
			prof[i] = cur;
		}
		lane.profile = prof;
		lane.greenLevel = lane.level(lane.length);
		List<Integer> teeSamples = new ArrayList<>();
		for (int a = -4; a <= 4; a += 2) {
			for (int b = -4; b <= 4; b += 2) teeSamples.add(g((int) lane.p[0][0] + a, (int) lane.p[0][1] + b));
		}
		int start = lane.level(6);
		lane.teeLevel = Math.clamp(median(teeSamples), start - 1, start + 2) + (int) lane.def.teeLift;
		if (Math.abs(lane.teeLevel - start) == 1 && lane.def.teeLift == 0) lane.teeLevel = start;
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (int v : prof) {
			lo = Math.min(lo, v);
			hi = Math.max(hi, v);
		}
		log.add(String.format("hole %d len=%.0f tee=%d green=%d play %d..%d", lane.def.n, lane.length, lane.teeLevel, lane.greenLevel, lo, hi));
	}

	// ---------------------------------------------------------------- building

	private void set(int x, int y, int z, BlockState state) {
		level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
	}

	private void cmd(String command) {
		server.getCommands().performPrefixedCommand(source, command);
	}

	private int surface(int x, int z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
	}

	/** Dirt up to y, the given top block at y and air above (clearing trees). */
	private void column(int x, int z, int y, BlockState top) {
		int natural = g(x, z);
		for (int yy = Math.min(natural, y) - 2; yy < y; yy++) {
			if (yy > natural - 3 || !level.getBlockState(new BlockPos(x, yy, z)).isSolid()) {
				set(x, yy, z, Blocks.DIRT.defaultBlockState());
			}
		}
		set(x, y, z, top);
		for (int yy = y + 1; yy <= Math.max(natural, y) + 32; yy++) {
			if (!level.getBlockState(new BlockPos(x, yy, z)).isAir()) set(x, yy, z, Blocks.AIR.defaultBlockState());
		}
	}

	private void build() {
		readGround();
		for (HoleDef def : HOLES) {
			Lane lane = new Lane(def);
			profile(lane);
			lanes.add(lane);
		}
		shapeTerrain();
		for (Lane lane : lanes) features(lane);
		for (Lane lane : lanes) markers(lane);
		paths();
		clubhouse();
		registerCourse();
	}

	private void shapeTerrain() {
		BlockState fairway = ModBlocks.FAIRWAY.defaultBlockState(), rough = ModBlocks.ROUGH.defaultBlockState();
		BlockState green = ModBlocks.GREEN.defaultBlockState(), grass = Blocks.GRASS_BLOCK.defaultBlockState();
		for (int x = ox; x < ox + W; x++) {
			for (int z = oz; z < oz + D; z++) {
				Lane best = null;
				double[] bp = null;
				double bestRatio = 1;
				for (Lane lane : lanes) {
					double[] pr = lane.project(x + 0.5, z + 0.5);
					if (pr[0] < -14 || pr[0] > lane.length + 16) continue;
					double ratio = pr[1] / lane.corridor(pr[0]);
					if (ratio < bestRatio) {
						bestRatio = ratio;
						best = lane;
						bp = pr;
					}
				}
				if (best == null) continue;
				double s = bp[0], d = bp[1];
				double half = best.playHalf(s), corr = best.corridor(s);
				double out = Math.max(Math.max(d - half, -8 - s), s - (best.length + 10));
				double w = Math.clamp(out / (corr - half), 0, 1);
				double blend = Math.clamp(w / 0.8, 0, 1);
				if (blend >= 1) continue; // wavy natural edge, trees stay
				blend = blend * blend * (3 - 2 * blend);

				double teeDist = Math.hypot(x + 0.5 - best.p[0][0], z + 0.5 - best.p[0][1]);
				double[] cup = best.p[best.p.length - 1];
				double cupDist = Math.hypot(x + 0.5 - cup[0], z + 0.5 - cup[1]);
				double base = best.level(s);
				if (teeDist <= 4.5) base = best.teeLevel;
				else if (teeDist <= 9 && best.teeLevel != base) base = best.teeLevel + (base - best.teeLevel) * (teeDist - 4.5) / 4.5;
				int target = (int) Math.round(base + (smooth(x, z, 2) - base) * blend);

				BlockState top;
				if (cupDist <= 7.2) top = green;
				else if (teeDist <= 3.5) top = fairway;
				else if (d <= best.fairwayHalf(s) && s >= 10 && s <= best.length - 7
						&& (best.def.par >= 4 || s >= best.length * 0.45)) top = fairway;
				else if (blend < 0.45) top = rough;
				else top = grass;
				column(x, z, target, top);
			}
		}
	}

	// ---------------------------------------------------------------- hazards

	private void features(Lane lane) {
		for (Feature f : lane.def.features) {
			double s = f.fromGreen ? lane.length + f.s : f.s;
			double[] c = lane.pointAt(s, f.lateral);
			switch (f.type) {
				case "bunker" -> bunker((int) Math.round(c[0]), (int) Math.round(c[1]), f.rx, f.rz);
				case "pond" -> {
					Set<Long> cells = ellipse((int) Math.round(c[0]), (int) Math.round(c[1]), f.rx, f.rz);
					int y = Integer.MAX_VALUE;
					for (long k : cells) y = Math.min(y, surface((int) (k >> 32), (int) k));
					water(cells, y);
				}
				case "creek" -> {
					Set<Long> cells = new HashSet<>();
					for (double a = -f.rz; a <= f.rz; a += 0.5) {
						for (double b = 0; b <= f.rx; b += 0.5) {
							double[] q = lane.pointAt(s + b, a);
							cells.add(key((int) Math.floor(q[0]), (int) Math.floor(q[1])));
						}
					}
					water(cells, lane.level(s));
					bridge(lane, s - 2, s + f.rx + 2, 9);
				}
				case "island" -> {
					double[] cup = lane.p[lane.p.length - 1];
					Set<Long> ring = new HashSet<>();
					for (int a = -12; a <= 12; a++) {
						for (int b = -12; b <= 12; b++) {
							double r = Math.hypot(a, b);
							if (r >= 7.5 && r <= 11.5) ring.add(key((int) cup[0] + a, (int) cup[1] + b));
						}
					}
					water(ring, lane.greenLevel);
					bridge(lane, lane.length + 7, lane.length + 13, 0);
				}
				default -> throw new IllegalArgumentException(f.type);
			}
		}
	}

	private static long key(int x, int z) {
		return ((long) x << 32) ^ (z & 0xffffffffL);
	}

	private static Set<Long> ellipse(int cx, int cz, int rx, int rz) {
		Set<Long> out = new HashSet<>();
		for (int a = -rx; a <= rx; a++) {
			for (int b = -rz; b <= rz; b++) {
				if ((double) a * a / (rx * rx) + (double) b * b / (rz * rz) <= 1) out.add(key(cx + a, cz + b));
			}
		}
		return out;
	}

	private void bunker(int cx, int cz, int rx, int rz) {
		for (long k : ellipse(cx, cz, rx, rz)) {
			int x = (int) (k >> 32), z = (int) k;
			int y = surface(x, z);
			BlockState s = level.getBlockState(new BlockPos(x, y, z));
			if (s.is(ModBlocks.FAIRWAY) || s.is(ModBlocks.ROUGH) || s.is(ModBlocks.GREEN) || s.is(Blocks.GRASS_BLOCK)) {
				set(x, y, z, ModBlocks.BUNKER_SAND.defaultBlockState());
			}
		}
	}

	/** Water two deep with its surface at y, cut into higher ground and with the rim raised so nothing spills. */
	private void water(Set<Long> cells, int y) {
		for (long k : cells) {
			int x = (int) (k >> 32), z = (int) k;
			int top = surface(x, z);
			for (int yy = y + 1; yy <= Math.max(top, y) + 6; yy++) set(x, yy, z, Blocks.AIR.defaultBlockState());
			for (int yy = top + 1; yy <= y - 2; yy++) set(x, yy, z, Blocks.DIRT.defaultBlockState());
			set(x, y - 2, z, Blocks.DIRT.defaultBlockState());
			set(x, y - 1, z, Blocks.WATER.defaultBlockState());
			set(x, y, z, Blocks.WATER.defaultBlockState());
		}
		for (long k : cells) {
			int x = (int) (k >> 32), z = (int) k;
			for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int nx = x + o[0], nz = z + o[1];
				if (cells.contains(key(nx, nz))) continue;
				int ny = surface(nx, nz);
				if (ny < y) {
					for (int yy = ny + 1; yy < y; yy++) set(nx, yy, nz, Blocks.DIRT.defaultBlockState());
					set(nx, y, nz, ModBlocks.ROUGH.defaultBlockState());
				}
			}
		}
	}

	/** Footbridge across water at the given lateral offset. */
	private void bridge(Lane lane, double s1, double s2, double lateral) {
		for (double s = s1; s <= s2; s += 0.5) {
			for (int w = 0; w <= 1; w++) {
				double[] q = lane.pointAt(s, lateral + w);
				int x = (int) Math.floor(q[0]), z = (int) Math.floor(q[1]);
				int y = surface(x, z);
				if (!level.getBlockState(new BlockPos(x, y, z)).getFluidState().isEmpty()) {
					set(x, y, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
				}
			}
		}
	}

	// ---------------------------------------------------------------- markers, paths, clubhouse

	private final List<int[]> tees = new ArrayList<>(), cups = new ArrayList<>();

	private void markers(Lane lane) {
		int tx = (int) Math.floor(lane.p[0][0]), tz = (int) Math.floor(lane.p[0][1]);
		double[] c = lane.p[lane.p.length - 1];
		int cx = (int) Math.floor(c[0]), cz = (int) Math.floor(c[1]);
		int ty = surface(tx, tz), cy = surface(cx, cz);
		set(tx, ty + 1, tz, ModBlocks.TEE_BOX.defaultBlockState());
		set(cx, cy, cz, ModBlocks.CUP.defaultBlockState());
		set(cx, cy + 1, cz, ModBlocks.FLAG.defaultBlockState());
		tees.add(new int[] {tx, ty + 1, tz});
		cups.add(new int[] {cx, cy, cz});
		double[] label = lane.pointAt(-3, -3);
		cmd(String.format("summon minecraft:text_display %.1f %.1f %.1f {Tags:[\"minegolf_demo\"],billboard:\"center\",text:{text:\"Hole %d · %s\",color:\"gold\",bold:true}}",
				label[0], ty + 3.4, label[1], lane.def.n, lane.def.name));
		cmd(String.format("summon minecraft:text_display %.1f %.1f %.1f {Tags:[\"minegolf_demo\"],billboard:\"center\",text:{text:\"Par %d · %.0f blocks\",color:\"white\"}}",
				label[0], ty + 3.0, label[1], lane.def.par, lane.length));
	}

	private void paths() {
		for (int k = 0; k + 1 < lanes.size(); k++) {
			Lane from = lanes.get(k), to = lanes.get(k + 1);
			double[] a = from.pointAt(from.length + 9, 0), b = to.pointAt(-6, 0);
			int steps = (int) Math.ceil(Math.hypot(b[0] - a[0], b[1] - a[1]));
			for (int i = 0; i <= steps; i++) {
				double x = a[0] + (b[0] - a[0]) * i / Math.max(1, steps), z = a[1] + (b[1] - a[1]) * i / Math.max(1, steps);
				for (int w = 0; w <= 1; w++) {
					int px = (int) Math.floor(x) + w, pz = (int) Math.floor(z);
					int y = surface(px, pz);
					BlockState s = level.getBlockState(new BlockPos(px, y, pz));
					if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(ModBlocks.ROUGH) || s.is(Blocks.PODZOL) || s.is(Blocks.COARSE_DIRT)) {
						set(px, y, pz, Blocks.DIRT_PATH.defaultBlockState());
					}
				}
			}
		}
	}

	private void clubhouse() {
		double[] spot = lanes.getFirst().pointAt(-6, -16);
		int x1 = (int) spot[0] - 5, z1 = (int) spot[1] - 4, x2 = x1 + 10, z2 = z1 + 8;
		List<Integer> hs = new ArrayList<>();
		for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) hs.add(g(x, z));
		int y = median(hs);
		for (int x = x1 - 2; x <= x2 + 2; x++) {
			for (int z = z1 - 2; z <= z2 + 4; z++) column(x, z, y, Blocks.GRASS_BLOCK.defaultBlockState());
		}
		int f = y + 1;
		cmd(String.format("fill %d %d %d %d %d %d minecraft:spruce_planks", x1, y, z1, x2, y, z2));
		cmd(String.format("fill %d %d %d %d %d %d minecraft:oak_planks hollow", x1, f, z1, x2, f + 3, z2));
		for (int[] c : new int[][] {{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
			cmd(String.format("fill %d %d %d %d %d %d minecraft:spruce_log", c[0], f, c[1], c[0], f + 3, c[1]));
		}
		cmd(String.format("fill %d %d %d %d %d %d minecraft:dark_oak_slab", x1 - 1, f + 4, z1 - 1, x2 + 1, f + 4, z2 + 1));
		cmd(String.format("fill %d %d %d %d %d %d minecraft:air", x1 + 4, f, z2, x1 + 6, f + 1, z2));
		cmd(String.format("fill %d %d %d %d %d %d minecraft:glass_pane", x1 + 2, f + 1, z1, x2 - 2, f + 2, z1));
		cmd(String.format("setblock %d %d %d minecraft:barrel", x1 + 5, f, z1 + 1));
		cmd(String.format("setblock %d %d %d minecraft:lantern", x1 + 3, f, z1 + 1));
		cmd(String.format("fill %d %d %d %d %d %d minecraft:dirt_path", x1 + 4, y, z2 + 1, x1 + 6, y, z2 + 3));
		cmd(String.format("summon minecraft:text_display %d %.1f %d {Tags:[\"minegolf_demo\"],billboard:\"center\",text:{text:\"%s\",color:\"green\",bold:true}}",
				x1 + 5, f + 5.5, z2, NAME));
	}

	private void registerCourse() {
		CourseSavedData data = CourseSavedData.get(server);
		Course course = data.create(NAME, new UUID(0, 0));
		for (int k = 0; k < lanes.size(); k++) {
			int[] t = tees.get(k), c = cups.get(k);
			course.addHole(new Hole(Level.OVERWORLD, new BlockPos(t[0], t[1], t[2]), new BlockPos(c[0], c[1], c[2]), HOLES[k].par));
			log.add("hole " + (k + 1) + " tee " + t[0] + " " + t[1] + " " + t[2] + "  cup " + c[0] + " " + c[1] + " " + c[2]);
		}
		data.setDirty();

		int[] t1 = tees.getFirst();
		double[] stand = lanes.getFirst().pointAt(-3, 2);
		server.setRespawnData(new LevelData.RespawnData(GlobalPos.of(Level.OVERWORLD, new BlockPos(t1[0], t1[1], t1[2])), 0F, 0F));

		ByteBuffer b = ByteBuffer.allocate(16).putLong(course.id().getMostSignificantBits()).putLong(course.id().getLeastSignificantBits());
		b.flip();
		String ids = b.getInt() + "," + b.getInt() + "," + b.getInt() + "," + b.getInt();
		String bag = "minegolf:golf_bag[container=[{slot:0,item:{id:\"minegolf:driver\",count:1}},{slot:1,item:{id:\"minegolf:iron\",count:1}},"
				+ "{slot:2,item:{id:\"minegolf:wedge\",count:1}},{slot:3,item:{id:\"minegolf:putter\",count:1}},{slot:4,item:{id:\"minegolf:golf_ball\",count:16}}]]";
		welcome.add("give @a minegolf:course_book[minegolf:course_id=[I;" + ids + "]]");
		welcome.add("give @a " + bag);
		welcome.add(String.format("tp @a %.1f %d %.1f facing %d %d %d", stand[0], t1[1], stand[1],
				(int) lanes.getFirst().p[1][0], t1[1], (int) lanes.getFirst().p[1][1]));
		welcome.add("gamemode creative @a");
		welcome.add("tellraw @a {text:\"Done! Welcome to MineGolf Links! 6 holes, par 22.\",color:\"green\"}");
		welcome.add("tellraw @a {text:\"Right-click the Course Book and press Start round, then tee off at hole 1.\",color:\"gray\"}");
	}

	// ---------------------------------------------------------------- preview

	private void renderPreview(Path file) throws IOException {
		int scale = 3;
		BufferedImage img = new BufferedImage((W + 2 * M) * scale, (D + 2 * M) * scale, BufferedImage.TYPE_INT_RGB);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int i = 0; i < W + 2 * M; i++) {
			for (int j = 0; j < D + 2 * M; j++) {
				int x = ox - M + i, z = oz - M + j;
				int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				BlockState s = level.getBlockState(p.set(x, y, z));
				int col = s.getMapColor(level, p).col;
				int north = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - 1) - 1;
				double shade = y > north ? 1.15 : y < north ? 0.78 : 1.0;
				if (s.is(ModBlocks.CUP) || s.is(ModBlocks.FLAG) || s.is(ModBlocks.TEE_BOX)) col = 0xFF2020;
				int r = Math.min(255, (int) (((col >> 16) & 255) * shade));
				int gg = Math.min(255, (int) (((col >> 8) & 255) * shade));
				int bb = Math.min(255, (int) ((col & 255) * shade));
				int rgb = (r << 16) | (gg << 8) | bb;
				for (int a = 0; a < scale; a++) for (int c = 0; c < scale; c++) img.setRGB(i * scale + a, j * scale + c, rgb);
			}
		}
		ImageIO.write(img, "png", file.toFile());
	}
}
