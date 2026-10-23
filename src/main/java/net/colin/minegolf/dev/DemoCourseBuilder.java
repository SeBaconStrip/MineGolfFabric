package net.colin.minegolf.dev;

import net.colin.minegolf.MineGolf;
import net.colin.minegolf.block.ModBlocks;
import net.colin.minegolf.course.Course;
import net.colin.minegolf.course.CourseSavedData;
import net.colin.minegolf.course.Hole;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Dev tool, only active in the development environment ({@code /minegolf_dev demo}): finds a fairly flat spot near spawn and lays a 6-hole course into the natural
 * terrain. Each hole's play surface is one flat level taken from the terrain; the rough blends back
 * into the landscape, trees between the lanes stay.
 */
public final class DemoCourseBuilder {
	private static DemoCourseBuilder current;

	private enum Stage {LOADING, WELCOME, DONE}

	private Stage stage = Stage.LOADING;
	private List<long[]> forced = List.of();
	private final List<String> welcome = new ArrayList<>();

	public static void register() {
		if (!FabricLoader.getInstance().isDevelopmentEnvironment()) return;
		CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> dispatcher.register(
				Commands.literal("minegolf_dev").then(Commands.literal("demo").executes(ctx -> {
					current = new DemoCourseBuilder(ctx.getSource().getServer());
					current.start();
					return 1;
				}))));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (current != null && current.server == server) current.tick();
		});
	}

	private void start() {
		findSite();
		forced = chunks();
		for (long[] c : forced) level.setChunkForced((int) c[0], (int) c[1], true);
		MineGolf.LOGGER.info("DEMO site {},{} - loading {} chunks", ox, oz, forced.size());
	}

	private void tick() {
		if (stage == Stage.LOADING) {
			for (long[] c : forced) {
				if (!level.getChunkSource().hasChunk((int) c[0], (int) c[1])) return;
			}
			build();
			try {
				renderPreview(server.getWorldPath(LevelResource.ROOT).resolve("minegolf_preview.png"));
			} catch (IOException e) {
				MineGolf.LOGGER.error("DEMO preview failed", e);
			}
			for (long[] c : forced) level.setChunkForced((int) c[0], (int) c[1], false);
			MineGolf.LOGGER.info("DEMO BUILD done {}", this);
			stage = Stage.WELCOME;
		} else if (stage == Stage.WELCOME && !server.getPlayerList().getPlayers().isEmpty()) {
			welcome.forEach(this::cmd);
			stage = Stage.DONE;
		}
	}

	static final String NAME = "MineGolf Links";
	static final int W = 256, D = 206; // course footprint in x and z
	private static final int CORRIDOR = 15, PLAY = 9;

	// number, tee dx, cup dx, lane dz, par, name — relative to the site origin
	record HoleDef(int n, int tee, int cup, int z, int par, String name) {
	}

	static final HoleDef[] HOLES = {
			new HoleDef(1, 192, 144, 17, 3, "Opener"),
			new HoleDef(2, 134, 24, 51, 4, "The Pond"),
			new HoleDef(3, 24, 56, 85, 3, "Island"),
			new HoleDef(4, 66, 236, 119, 5, "The Long One"),
			new HoleDef(5, 232, 144, 153, 4, "Lookout"),
			new HoleDef(6, 134, 189, 187, 3, "Home"),
	};

	private final MinecraftServer server;
	private final ServerLevel level;
	private final CommandSourceStack source;
	int ox, oz;
	private int[][] ground; // natural ground height over the footprint + margin
	private static final int M = 20;
	final List<String> log = new ArrayList<>();
	final List<int[]> tees = new ArrayList<>(), cups = new ArrayList<>();

	DemoCourseBuilder(MinecraftServer server) {
		this.server = server;
		this.level = server.overworld();
		this.source = server.createCommandSourceStack().withSuppressedOutput();
	}

	// ---------------------------------------------------------------- site search

	void findSite() {
		ChunkGenerator gen = level.getChunkSource().getGenerator();
		RandomState rs = level.getChunkSource().randomState();
		BlockPos spawn = level.getRespawnData().globalPos().pos();
		record Candidate(int x, int z, double score) {
		}
		List<Candidate> list = new ArrayList<>();
		for (int cx = -640; cx <= 640; cx += 64) {
			for (int cz = -640; cz <= 640; cz += 64) {
				int x0 = spawn.getX() + cx - W / 2, z0 = spawn.getZ() + cz - D / 2;
				double sum = 0, sq = 0;
				int n = 0, wet = 0, worst = 0;
				for (HoleDef h : HOLES) {
					int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
					for (int s = Math.min(h.tee, h.cup) - 8; s <= Math.max(h.tee, h.cup) + 8; s += 8) {
						for (int dz = -12; dz <= 12; dz += 12) {
							int x = x0 + s, z = z0 + h.z + dz;
							int top = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, rs);
							int floor = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
							if (floor < top) wet++;
							sum += top;
							sq += (double) top * top;
							n++;
							lo = Math.min(lo, top);
							hi = Math.max(hi, top);
						}
					}
					worst = Math.max(worst, hi - lo);
				}
				double mean = sum / n;
				double std = Math.sqrt(Math.max(0, sq / n - mean * mean));
				double score = std * 2 + worst + wet * 6 + (mean < 64 ? 40 : 0) + (mean > 110 ? 40 : 0)
						+ Math.hypot(cx, cz) / 64.0;
				list.add(new Candidate(x0, z0, score));
			}
		}
		list.sort((a, b) -> Double.compare(a.score, b.score));
		for (Candidate c : list.subList(0, Math.min(15, list.size()))) {
			BlockPos mid = new BlockPos(c.x + W / 2, 80, c.z + D / 2);
			level.getChunk(mid); // generates the centre chunk only
			var biome = level.getBiome(mid);
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

	/** Chunks covering the footprint plus margin. */
	List<long[]> chunks() {
		List<long[]> out = new ArrayList<>();
		for (int cx = (ox - M) >> 4; cx <= (ox + W + M) >> 4; cx++) {
			for (int cz = (oz - M) >> 4; cz <= (oz + D + M) >> 4; cz++) out.add(new long[] {cx, cz});
		}
		return out;
	}

	// ---------------------------------------------------------------- terrain

	private boolean isGround(BlockState s) {
		if (s.isAir() || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(BlockTags.REPLACEABLE)
				|| s.is(BlockTags.FLOWERS) || s.is(Blocks.SNOW) || s.is(Blocks.BAMBOO) || s.is(Blocks.SUGAR_CANE)
				|| s.is(Blocks.PUMPKIN) || s.is(Blocks.MELON) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.MOSS_CARPET)) {
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
		int i = Math.clamp(x - ox + M, 0, W + 2 * M - 1), j = Math.clamp(z - oz + M, 0, D + 2 * M - 1);
		return ground[i][j];
	}

	/** Natural ground averaged over a small square, so single holes and bumps do not show. */
	private double smooth(int x, int z, int r) {
		double sum = 0;
		int n = 0;
		for (int a = -r; a <= r; a += 2) {
			for (int b = -r; b <= r; b += 2) {
				sum += g(x + a, z + b);
				n++;
			}
		}
		return sum / n;
	}

	private int median(List<Integer> values) {
		values.sort(Integer::compare);
		return values.get(values.size() / 2);
	}

	// ---------------------------------------------------------------- building

	private void set(int x, int y, int z, BlockState state) {
		level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
	}

	private void cmd(String command) {
		server.getCommands().performPrefixedCommand(source, command);
	}

	/** Sets the column to dirt up to y, the given top block at y and air above (clearing trees). */
	private void column(int x, int z, int y, BlockState top) {
		int natural = g(x, z);
		for (int yy = Math.min(natural, y) - 2; yy < y; yy++) {
			BlockState s = level.getBlockState(new BlockPos(x, yy, z));
			if (yy > natural - 3 || !s.isSolid()) set(x, yy, z, Blocks.DIRT.defaultBlockState());
		}
		set(x, y, z, top);
		for (int yy = y + 1; yy <= Math.max(natural, y) + 32; yy++) {
			BlockPos p = new BlockPos(x, yy, z);
			if (!level.getBlockState(p).isAir()) set(x, yy, z, Blocks.AIR.defaultBlockState());
		}
	}

	void build() {
		readGround();
		int[] levels = new int[HOLES.length];
		for (int k = 0; k < HOLES.length; k++) levels[k] = buildHole(HOLES[k]);
		paths(levels);
		clubhouse();
		registerAndWelcome();
	}

	private int buildHole(HoleDef h) {
		int d = h.cup > h.tee ? 1 : -1;
		int len = Math.abs(h.cup - h.tee);
		int teeX = ox + h.tee, cupX = ox + h.cup, z = oz + h.z;

		List<Integer> playSamples = new ArrayList<>();
		for (int s = 6; s <= len + 6; s += 2) {
			for (int dz = -6; dz <= 6; dz += 3) playSamples.add((int) Math.round(smooth(teeX + d * s, z + dz, 2)));
		}
		int play = median(playSamples);
		List<Integer> teeSamples = new ArrayList<>();
		for (int a = -4; a <= 4; a += 2) {
			for (int b = -4; b <= 4; b += 2) teeSamples.add(g(teeX + a, z + b));
		}
		int tee = Math.clamp(median(teeSamples), play - 2, play + 4);
		if (h.n == 5) tee = Math.max(tee, play + 4); // Lookout: elevated tee
		if (Math.abs(tee - play) == 1) tee = play;
		log.add("hole " + h.n + " play=" + play + " tee=" + tee + " len=" + len);

		BlockState fairway = ModBlocks.FAIRWAY.defaultBlockState(), rough = ModBlocks.ROUGH.defaultBlockState();
		BlockState green = ModBlocks.GREEN.defaultBlockState(), grass = Blocks.GRASS_BLOCK.defaultBlockState();
		for (int s = -14; s <= len + 16; s++) {
			for (int dz = -CORRIDOR; dz <= CORRIDOR; dz++) {
				int x = teeX + d * s, zz = z + dz;
				double teeDist = Math.hypot(s, dz);
				double cupDist = Math.hypot(s - len, dz);
				// 0 inside the play area, 1 at the corridor edge
				double out = Math.max(Math.max(Math.abs(dz) - PLAY, -8 - s), s - (len + 10));
				double w = Math.clamp(out / (CORRIDOR - PLAY), 0, 1);
				int base = play;
				if (teeDist <= 4.5) base = tee;
				else if (teeDist <= 9 && tee != play) {
					base = (int) Math.round(tee + (play - tee) * ((teeDist - 4.5) / 4.5));
				}
				int target = (int) Math.round(base + (smooth(x, zz, 2) - base) * w * w);
				BlockState top;
				if (w >= 1) top = grass;
				else if (cupDist <= 7) top = green;
				else if (teeDist <= 3.5) top = fairway;
				else if (Math.abs(dz) <= 5 && s >= 10 && s <= len - 6 && (h.par >= 4 || s >= len * 0.45)) top = fairway;
				else if (w > 0.6) top = grass;
				else top = rough;
				column(x, zz, target, top);
			}
		}

		// Hazards
		if (h.n == 1) {
			bunker(teeX, z, d, len - 10, -4, 3, play);
			bunker(teeX, z, d, len + 5, 6, 2, play);
		} else if (h.n == 2) {
			pond(teeX + d * 46, z, 6, 13, play);
			bridge(teeX + d * 38, teeX + d * 54, z + 9, play);
			bunker(teeX, z, d, len, 9, 3, play);
			bunker(teeX, z, d, len - 30, -6, 3, play);
		} else if (h.n == 3) {
			for (int dz = -11; dz <= 11; dz++) {
				for (int dx = -11; dx <= 11; dx++) {
					double r = Math.hypot(dx, dz);
					if (r >= 7.5 && r <= 11.5) water(cupX + dx, z + dz, play);
				}
			}
			bridge(cupX + d * 7, cupX + d * 12, z, play);
		} else if (h.n == 4) {
			for (int s = 76; s <= 78; s++) {
				for (int dz = -11; dz <= 11; dz++) water(teeX + d * s, z + dz, play);
			}
			bridge(teeX + d * 74, teeX + d * 80, z + 9, play);
			bunker(teeX, z, d, 100, 7, 3, play);
			bunker(teeX, z, d, 118, -7, 3, play);
			bunker(teeX, z, d, len - 9, 0, 2, play);
			bunker(teeX, z, d, len + 3, -8, 3, play);
		} else if (h.n == 5) {
			bunker(teeX, z, d, 58, -6, 3, play);
			bunker(teeX, z, d, 64, 6, 2, play);
			bunker(teeX, z, d, len + 8, 0, 3, play);
		} else if (h.n == 6) {
			bunker(teeX, z, d, len - 10, 0, 2, play);
			bunker(teeX, z, d, len, 8, 3, play);
			bunker(teeX, z, d, len + 9, 0, 3, play);
			pond(cupX - d * 2, z - 12, 5, 3, play);
		}

		set(teeX, tee + 1, z, ModBlocks.TEE_BOX.defaultBlockState());
		set(cupX, play, z, ModBlocks.CUP.defaultBlockState());
		set(cupX, play + 1, z, ModBlocks.FLAG.defaultBlockState());
		tees.add(new int[] {teeX, tee + 1, z});
		cups.add(new int[] {cupX, play, z});

		int lx = teeX - d * 3, lz = z - 3;
		cmd("summon minecraft:text_display " + lx + " " + (tee + 3.4) + " " + lz
				+ " {Tags:[\"minegolf_demo\"],billboard:\"center\",text:{text:\"Hole " + h.n + " · " + h.name + "\",color:\"gold\",bold:true}}");
		cmd("summon minecraft:text_display " + lx + " " + (tee + 3.0) + " " + lz
				+ " {Tags:[\"minegolf_demo\"],billboard:\"center\",text:{text:\"Par " + h.par + " · " + len + " blocks\",color:\"white\"}}");
		return play;
	}

	private void bunker(int teeX, int z, int d, int s, int dz, int r, int y) {
		int cx = teeX + d * s, cz = z + dz;
		for (int a = -r; a <= r; a++) {
			for (int b = -r; b <= r; b++) {
				if (a * a + b * b * 1.5 <= r * r + 0.5) set(cx + a, y, cz + b, ModBlocks.BUNKER_SAND.defaultBlockState());
			}
		}
	}

	private void water(int x, int z, int y) {
		set(x, y - 2, z, Blocks.DIRT.defaultBlockState());
		set(x, y - 1, z, Blocks.WATER.defaultBlockState());
		set(x, y, z, Blocks.WATER.defaultBlockState());
	}

	private void pond(int cx, int cz, int rx, int rz, int y) {
		for (int a = -rx; a <= rx; a++) {
			for (int b = -rz; b <= rz; b++) {
				if ((double) a * a / (rx * rx) + (double) b * b / (rz * rz) <= 1) water(cx + a, cz + b, y);
			}
		}
	}

	private void bridge(int x1, int x2, int z, int y) {
		for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
			for (int dz = 0; dz <= 1; dz++) set(x, y, z + dz, Blocks.SPRUCE_PLANKS.defaultBlockState());
		}
	}

	/** Dirt paths from each green to the next tee, following the ground. */
	private void paths(int[] levels) {
		for (int k = 0; k + 1 < HOLES.length; k++) {
			int[] from = cups.get(k), to = tees.get(k + 1);
			int x0 = from[0], z0 = from[2] + (to[2] > from[2] ? 8 : -8), x1 = to[0], z1 = to[2] + (to[2] > from[2] ? -5 : 5);
			int steps = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
			for (int i = 0; i <= steps; i++) {
				int x = x0 + (x1 - x0) * i / Math.max(1, steps), z = z0 + (z1 - z0) * i / Math.max(1, steps);
				for (int w = 0; w <= 1; w++) {
					int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + w, z) - 1;
					BlockState s = level.getBlockState(new BlockPos(x + w, y, z));
					if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(ModBlocks.ROUGH)) {
						set(x + w, y, z, Blocks.DIRT_PATH.defaultBlockState());
						set(x + w, y + 1, z, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
	}

	private void clubhouse() {
		int[] t1 = tees.getFirst();
		int x1 = t1[0] + 8, z1 = t1[2] - 9, x2 = x1 + 10, z2 = z1 + 8;
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

	private void registerAndWelcome() {
		CourseSavedData data = CourseSavedData.get(server);
		Course course = data.create(NAME, new UUID(0, 0));
		for (int k = 0; k < HOLES.length; k++) {
			int[] t = tees.get(k), c = cups.get(k);
			course.addHole(new Hole(Level.OVERWORLD, new BlockPos(t[0], t[1], t[2]), new BlockPos(c[0], c[1], c[2]), HOLES[k].par));
		}
		data.setDirty();

		int[] t1 = tees.getFirst();
		BlockPos spawn = new BlockPos(t1[0] + 3, t1[1], t1[2] + 3);
		server.setRespawnData(new LevelData.RespawnData(GlobalPos.of(Level.OVERWORLD, spawn), 90F, 10F));

		ByteBuffer b = ByteBuffer.allocate(16).putLong(course.id().getMostSignificantBits()).putLong(course.id().getLeastSignificantBits());
		b.flip();
		String ids = b.getInt() + "," + b.getInt() + "," + b.getInt() + "," + b.getInt();
		String bag = "minegolf:golf_bag[container=[{slot:0,item:{id:\"minegolf:driver\",count:1}},{slot:1,item:{id:\"minegolf:iron\",count:1}},"
				+ "{slot:2,item:{id:\"minegolf:wedge\",count:1}},{slot:3,item:{id:\"minegolf:putter\",count:1}},{slot:4,item:{id:\"minegolf:golf_ball\",count:16}}]]";
		welcome.add("give @a minegolf:course_book[minegolf:course_id=[I;" + ids + "]]");
		welcome.add("give @a " + bag);
		welcome.add("tp @a " + (t1[0] + 3) + " " + t1[1] + " " + (t1[2] + 3) + " 90 10");
		welcome.add("gamemode creative @a");
		welcome.add("tellraw @a {text:\"Welcome to MineGolf Links! 6 holes, par 22.\",color:\"green\"}");
		welcome.add("tellraw @a {text:\"Right-click the Course Book and press Start round, then tee off at hole 1.\",color:\"gray\"}");
	}

	// ---------------------------------------------------------------- preview

	void renderPreview(Path file) throws IOException {
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
				double shade = y > north ? 1.12 : y < north ? 0.82 : 1.0;
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

	@Override
	public String toString() {
		return String.join("\n", log) + "\ntees=" + Arrays.deepToString(tees.toArray()) + "\ncups=" + Arrays.deepToString(cups.toArray());
	}
}
