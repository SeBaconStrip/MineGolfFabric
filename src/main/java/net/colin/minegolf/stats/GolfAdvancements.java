package net.colin.minegolf.stats;

import net.colin.minegolf.MineGolf;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Advancements in the MineGolf tab. Apart from the root they use the "impossible" trigger and are
 * granted from code, see tools/gen_assets.py for the tree.
 */
public final class GolfAdvancements {
	public static final String FORE = "fore";
	public static final String COURSE_ARCHITECT = "course_architect";
	public static final String FULLY_EQUIPPED = "fully_equipped";
	public static final String IN_THE_CUP = "in_the_cup";
	public static final String BIRDIE = "birdie";
	public static final String EAGLE = "eagle";
	public static final String HOLE_IN_ONE = "hole_in_one";
	public static final String SNOWMAN = "snowman";
	public static final String QUITTER = "quitter";
	public static final String UNDER_PAR = "under_par";
	public static final String PERFECT_ROUND = "perfect_round";
	public static final String FULL_18 = "full_18";
	public static final String LONG_DRIVE = "long_drive";
	public static final String BEACH_DAY = "beach_day";
	public static final String SAND_WIZARD = "sand_wizard";
	public static final String SPLASH = "splash";
	public static final String FISH_FOOD = "fish_food";
	public static final String HOT_SHOT = "hot_shot";
	public static final String OVERTHINKER = "overthinker";

	public static final int FISH_FOOD_BALLS = 100;
	public static final int OVERTHINKER_SWINGS = 10;

	private GolfAdvancements() {
	}

	public static void award(ServerPlayer player, String name) {
		AdvancementHolder holder = player.level().getServer().getAdvancements()
				.get(Identifier.fromNamespaceAndPath(MineGolf.MOD_ID, "golf/" + name));
		if (holder == null) {
			MineGolf.LOGGER.warn("Missing advancement minegolf:golf/{}", name);
			return;
		}
		player.getAdvancements().award(holder, "done");
	}
}
