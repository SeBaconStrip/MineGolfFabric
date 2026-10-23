package net.colin.minegolf.round;

import net.colin.minegolf.course.Course;
import net.colin.minegolf.course.Hole;
import net.colin.minegolf.entity.GolfBallEntity;
import net.colin.minegolf.entity.LevelSurfaceLookup;
import net.colin.minegolf.network.ModPayloads;
import net.colin.minegolf.stats.GolfAdvancements;
import net.colin.minegolf.stats.GolfStats;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Active rounds of the running server. Rounds live in memory only and end when the server stops. */
public final class RoundManager {
	private static final double INVITE_RANGE = 32;

	private static @Nullable RoundManager instance;

	private final MinecraftServer server;
	private final Map<Integer, Round> rounds = new HashMap<>();
	private final Map<UUID, Round> byPlayer = new HashMap<>();
	private int nextId = 1;

	private RoundManager(MinecraftServer server) {
		this.server = server;
	}

	public static RoundManager get(MinecraftServer server) {
		if (instance == null || instance.server != server) instance = new RoundManager(server);
		return instance;
	}

	public static void reset() {
		instance = null;
	}

	public @Nullable Round roundOf(Player player) {
		return byPlayer.get(player.getUUID());
	}

	// ------------------------------------------------------------- joining and leaving

	public void start(ServerPlayer host, Course course) {
		List<Hole> holes = course.holes().stream().filter(h -> h.isIntact(server)).toList();
		if (holes.isEmpty()) {
			host.sendSystemMessage(Component.translatable("minegolf.msg.no_valid_holes"));
			return;
		}
		if (holes.size() < course.holes().size()) {
			host.sendSystemMessage(Component.translatable("minegolf.msg.holes_skipped", course.holes().size() - holes.size()));
		}
		leave(host);

		Round round = new Round(nextId++, course.name(), holes);
		rounds.put(round.id, round);
		addPlayer(round, host);
		host.sendSystemMessage(Component.translatable("minegolf.msg.round_started", course.name(), holes.size()));

		MutableComponent joinButton = Component.translatable("minegolf.msg.join_button").withStyle(style -> style
				.withColor(ChatFormatting.GREEN)
				.withClickEvent(new ClickEvent.RunCommand("/minegolf join " + round.id))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable("minegolf.msg.join_hover"))));
		for (ServerPlayer other : host.level().players()) {
			if (other != host && other.distanceTo(host) <= INVITE_RANGE && !byPlayer.containsKey(other.getUUID())) {
				other.sendSystemMessage(Component.translatable("minegolf.msg.invite", host.getName(), course.name())
						.append(" ").append(joinButton));
			}
		}
		describeHole(round);
		broadcast(round);
	}

	public void join(ServerPlayer player, int roundId) {
		Round round = rounds.get(roundId);
		if (round == null) {
			player.sendSystemMessage(Component.translatable("minegolf.msg.no_round"));
			return;
		}
		if (byPlayer.get(player.getUUID()) == round) {
			player.sendSystemMessage(Component.translatable("minegolf.msg.already_in_round"));
			return;
		}
		if (!round.joinOpen) {
			player.sendSystemMessage(Component.translatable("minegolf.msg.join_closed"));
			return;
		}
		leave(player);
		addPlayer(round, player);
		announce(round, Component.translatable("minegolf.msg.joined", player.getName()));
		describeHole(round);
		broadcast(round);
	}

	private void addPlayer(Round round, ServerPlayer player) {
		round.card.addPlayer(player.getUUID(), player.getName().getString());
		byPlayer.put(player.getUUID(), round);
	}

	/** Ends the round for this player on request, with feedback. Others in the group play on. */
	public void quit(ServerPlayer player) {
		Round round = byPlayer.get(player.getUUID());
		if (round == null) {
			player.sendSystemMessage(Component.translatable("minegolf.msg.not_in_round"));
			return;
		}
		leave(player);
		player.sendSystemMessage(Component.translatable("minegolf.msg.you_left", round.courseName));
	}

	/** Removes the player from their round, if any. */
	public void leave(ServerPlayer player) {
		Round round = byPlayer.remove(player.getUUID());
		if (round == null) return;
		removeBall(round, player.getUUID());
		round.card.removePlayer(player.getUUID());
		ServerPlayNetworking.send(player, ModPayloads.RoundClear.INSTANCE);
		if (round.card.players().isEmpty()) {
			rounds.remove(round.id);
			return;
		}
		announce(round, Component.translatable("minegolf.msg.left", player.getName()));
		checkHoleComplete(round);
	}

	// ------------------------------------------------------------- playing

	/** Places the player's ball on the current hole's tee. Returns false (with a message) if not allowed. */
	public boolean teeUp(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack stack) {
		Round round = byPlayer.get(player.getUUID());
		if (round == null || round.isOver()) return false;
		UUID id = player.getUUID();
		Hole hole = round.hole();
		if (round.card.isFinished(id, round.currentHole)) {
			player.sendOverlayMessage(Component.translatable("minegolf.msg.wait_for_others"));
			return false;
		}
		if (ball(round, id) != null) {
			player.sendOverlayMessage(Component.translatable("minegolf.msg.already_teed"));
			return false;
		}
		if (!level.dimension().equals(hole.dimension()) || !pos.equals(hole.tee())) {
			player.sendOverlayMessage(Component.translatable("minegolf.msg.wrong_tee", round.currentHole + 1,
					hole.tee().getX(), hole.tee().getY(), hole.tee().getZ()));
			return false;
		}
		if (!hole.isIntact(server)) {
			skipHole(round);
			return false;
		}
		double top = new LevelSurfaceLookup(level).height(pos.getX(), pos.getY(), pos.getZ());
		GolfBallEntity ball = GolfBallEntity.place(level, player, stack, pos.getX() + 0.5, pos.getY() + top, pos.getZ() + 0.5, round.id);
		stack.consume(1, player);
		round.balls.put(id, ball.getUUID());
		return true;
	}

	/** Whether this player may swing at this ball right now. */
	public boolean canHit(Player player, GolfBallEntity ball) {
		if (ball.roundId() == GolfBallEntity.PRACTICE) return true;
		Round round = roundFor(ball);
		return round != null && byPlayer.get(player.getUUID()) == round && !round.card.isFinished(player.getUUID(), round.currentHole);
	}

	public void onStroke(ServerPlayer player, GolfBallEntity ball) {
		Round round = roundFor(ball);
		if (round == null) return;
		round.card.addStroke(player.getUUID(), round.currentHole);
		round.joinOpen = false;
		broadcast(round);
	}

	public void onBallRest(GolfBallEntity ball) {
		Round round = roundFor(ball);
		UUID owner = ball.ownerId();
		if (round != null && owner != null && round.card.reachedCap(owner, round.currentHole)) capOut(round, owner);
	}

	public void onPenalty(GolfBallEntity ball) {
		ServerPlayer owner = ball.ownerPlayer();
		Round round = roundFor(ball);
		if (round == null) {
			if (owner != null) owner.sendOverlayMessage(Component.translatable("minegolf.msg.practice_hazard"));
			return;
		}
		UUID id = ball.ownerId();
		if (id == null) return;
		round.card.addStroke(id, round.currentHole);
		if (owner != null) {
			owner.sendOverlayMessage(Component.translatable("minegolf.msg.penalty"));
			owner.awardStat(GolfStats.PENALTY_STROKES);
		}
		if (round.card.reachedCap(id, round.currentHole)) capOut(round, id);
		else broadcast(round);
	}

	public void onHoled(GolfBallEntity ball) {
		Round round = roundFor(ball);
		UUID id = ball.ownerId();
		ball.returnToOwner();
		ball.discard();
		if (round == null || id == null) return;
		round.balls.remove(id);
		int strokes = round.card.strokes(id, round.currentHole);
		int par = round.hole().par();
		round.card.finishHole(id, round.currentHole);
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		if (player != null) recordHole(player, strokes, par, round.hole().length());
		announce(round, Component.translatable("minegolf.msg.holed_out", round.card.name(id), strokes,
				Component.translatable(scoreName(strokes, par))));
		checkHoleComplete(round);
	}

	public void pickup(ServerPlayer player) {
		Round round = byPlayer.get(player.getUUID());
		if (round == null || round.isOver()) {
			player.sendSystemMessage(Component.translatable("minegolf.msg.not_in_round"));
			return;
		}
		if (round.card.isFinished(player.getUUID(), round.currentHole)) {
			player.sendOverlayMessage(Component.translatable("minegolf.msg.wait_for_others"));
			return;
		}
		round.card.pickup(player.getUUID(), round.currentHole);
		GolfAdvancements.award(player, GolfAdvancements.QUITTER);
		removeBall(round, player.getUUID());
		announce(round, Component.translatable("minegolf.msg.picked_up", player.getName()));
		checkHoleComplete(round);
	}

	private void capOut(Round round, UUID id) {
		round.card.pickup(id, round.currentHole);
		removeBall(round, id);
		announce(round, Component.translatable("minegolf.msg.capped", round.card.name(id), round.card.strokes(id, round.currentHole)));
		checkHoleComplete(round);
	}

	private void skipHole(Round round) {
		announce(round, Component.translatable("minegolf.msg.hole_broken", round.currentHole + 1));
		for (UUID id : round.card.players()) removeBall(round, id);
		round.card.voidHole(round.currentHole);
		checkHoleComplete(round);
	}

	private void checkHoleComplete(Round round) {
		if (round.isOver()) return;
		if (!round.card.holeComplete(round.currentHole)) {
			broadcast(round);
			return;
		}
		MutableComponent summary = Component.translatable("minegolf.msg.hole_done", round.currentHole + 1);
		for (UUID id : round.card.players()) {
			summary.append(Component.literal("  " + round.card.name(id) + ": " + round.card.strokes(id, round.currentHole)));
		}
		announce(round, summary);
		round.currentHole++;
		if (round.isOver()) {
			finish(round);
		} else {
			describeHole(round);
			broadcast(round);
		}
	}

	private void finish(Round round) {
		int holes = round.card.holeCount();
		int[] pars = new int[holes];
		for (int h = 0; h < holes; h++) pars[h] = round.card.par(h);
		List<ModPayloads.ScoreRow> rows = new ArrayList<>();
		for (UUID id : round.card.players()) {
			int[] strokes = new int[holes];
			for (int h = 0; h < holes; h++) strokes[h] = round.card.strokes(id, h);
			rows.add(new ModPayloads.ScoreRow(round.card.name(id), strokes, round.card.total(id), round.card.relativeToPar(id)));
			announce(round, Component.translatable("minegolf.msg.final_score", round.card.name(id),
					round.card.total(id), formatToPar(round.card.relativeToPar(id))));
		}
		rows.sort((a, b) -> Integer.compare(a.total(), b.total()));
		int[] lengths = round.holes.stream().mapToInt(Hole::length).toArray();
		for (UUID id : round.card.players()) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) recordRound(player, round.card, lengths);
		}
		ModPayloads.RoundEnd end = new ModPayloads.RoundEnd(round.courseName, pars, rows);
		send(round, end);
		send(round, ModPayloads.RoundClear.INSTANCE);
		for (UUID id : round.card.players()) byPlayer.remove(id);
		rounds.remove(round.id);
	}

	// ------------------------------------------------------------- stats and achievements

	private static void recordHole(ServerPlayer player, int strokes, int par, int length) {
		player.awardStat(GolfStats.HOLES_PLAYED);
		int toPar = strokes - par;
		if (strokes == 1) player.awardStat(GolfStats.HOLES_IN_ONE);
		else if (toPar == -1) player.awardStat(GolfStats.BIRDIES);
		else if (toPar == -2) player.awardStat(GolfStats.EAGLES);

		GolfAdvancements.award(player, GolfAdvancements.IN_THE_CUP);
		int fairToPar = strokes - GolfRules.effectivePar(par, length);
		if (fairToPar <= -1) GolfAdvancements.award(player, GolfAdvancements.BIRDIE);
		if (fairToPar <= -2) GolfAdvancements.award(player, GolfAdvancements.EAGLE);
		if (GolfRules.isHoleInOne(strokes, length)) GolfAdvancements.award(player, GolfAdvancements.HOLE_IN_ONE);
		if (strokes == GolfRules.SNOWMAN) GolfAdvancements.award(player, GolfAdvancements.SNOWMAN);
	}

	private static void recordRound(ServerPlayer player, Scorecard card, int[] lengths) {
		UUID id = player.getUUID();
		player.awardStat(GolfStats.ROUNDS_FINISHED);
		if (GolfRules.isFullEighteen(card, id)) GolfAdvancements.award(player, GolfAdvancements.FULL_18);
		if (GolfRules.isUnderParRound(card, id, lengths)) GolfAdvancements.award(player, GolfAdvancements.UNDER_PAR);
		if (GolfRules.isPerfectRound(card, id, lengths)) GolfAdvancements.award(player, GolfAdvancements.PERFECT_ROUND);
	}

	// ------------------------------------------------------------- helpers

	private @Nullable Round roundFor(GolfBallEntity ball) {
		Round round = rounds.get(ball.roundId());
		return round != null && round.balls.containsValue(ball.getUUID()) ? round : null;
	}

	private @Nullable GolfBallEntity ball(Round round, UUID player) {
		UUID ballId = round.balls.get(player);
		if (ballId == null) return null;
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(ballId);
			if (entity instanceof GolfBallEntity ball && ball.isAlive()) return ball;
		}
		round.balls.remove(player);
		return null;
	}

	private void removeBall(Round round, UUID player) {
		GolfBallEntity ball = ball(round, player);
		round.balls.remove(player);
		if (ball != null) {
			ball.returnToOwner();
			ball.discard();
		}
	}

	private void describeHole(Round round) {
		Hole hole = round.hole();
		announce(round, Component.translatable("minegolf.msg.next_hole", round.currentHole + 1, round.holes.size(),
				hole.par(), hole.length(), hole.tee().getX(), hole.tee().getY(), hole.tee().getZ()));
	}

	private void announce(Round round, Component message) {
		for (UUID id : round.card.players()) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) p.sendSystemMessage(message);
		}
	}

	private void send(Round round, CustomPacketPayload payload) {
		for (UUID id : round.card.players()) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) ServerPlayNetworking.send(p, payload);
		}
	}

	private void broadcast(Round round) {
		if (round.isOver()) return;
		List<ModPayloads.PlayerLine> lines = new ArrayList<>();
		for (UUID id : round.card.players()) {
			lines.add(new ModPayloads.PlayerLine(id, round.card.name(id), round.card.strokes(id, round.currentHole),
					round.card.isFinished(id, round.currentHole), round.card.relativeToPar(id)));
		}
		Hole hole = round.hole();
		send(round, new ModPayloads.RoundState(round.id, round.courseName, round.currentHole + 1, round.holes.size(),
				hole.par(), hole.tee(), hole.cup(), lines));
	}

	public static String formatToPar(int toPar) {
		return toPar == 0 ? "E" : (toPar > 0 ? "+" + toPar : String.valueOf(toPar));
	}

	private static String scoreName(int strokes, int par) {
		if (strokes == 1) return "minegolf.score.hole_in_one";
		return switch (strokes - par) {
			case -3 -> "minegolf.score.albatross";
			case -2 -> "minegolf.score.eagle";
			case -1 -> "minegolf.score.birdie";
			case 0 -> "minegolf.score.par";
			case 1 -> "minegolf.score.bogey";
			case 2 -> "minegolf.score.double_bogey";
			default -> strokes - par < 0 ? "minegolf.score.under" : "minegolf.score.over";
		};
	}
}
