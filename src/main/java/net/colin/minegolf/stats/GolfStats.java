package net.colin.minegolf.stats;

import net.colin.minegolf.MineGolf;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;

/** Custom statistics, listed under "General" in the statistics screen and usable in scoreboards. */
public final class GolfStats {
	public static final Identifier STROKES = register("strokes");
	public static final Identifier HOLES_PLAYED = register("holes_played");
	public static final Identifier ROUNDS_FINISHED = register("rounds_finished");
	public static final Identifier HOLES_IN_ONE = register("holes_in_one");
	public static final Identifier BIRDIES = register("birdies");
	public static final Identifier EAGLES = register("eagles");
	public static final Identifier PENALTY_STROKES = register("penalty_strokes");
	public static final Identifier BALLS_IN_WATER = register("balls_in_water");
	public static final Identifier WASTED_SWINGS = register("wasted_swings");
	/** In blocks, the best value ever reached rather than a sum. */
	public static final Identifier LONGEST_DRIVE = register("longest_drive");

	private GolfStats() {
	}

	private static Identifier register(String name) {
		Identifier id = Identifier.fromNamespaceAndPath(MineGolf.MOD_ID, name);
		Registry.register(BuiltInRegistries.CUSTOM_STAT, id, id);
		Stats.CUSTOM.get(id, StatFormatter.DEFAULT);
		return id;
	}

	/** Loads the class so the stats are registered during mod init. */
	public static void register() {
	}

	public static int get(ServerPlayer player, Identifier stat) {
		return player.getStats().getValue(Stats.CUSTOM.get(stat));
	}

	public static void recordDrive(ServerPlayer player, int blocks) {
		Stat<Identifier> stat = Stats.CUSTOM.get(LONGEST_DRIVE);
		if (player.getStats().getValue(stat) < blocks) player.getStats().setValue(player, stat, blocks);
	}
}
