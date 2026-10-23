package net.colin.minegolf.round;

import java.util.UUID;

/**
 * Score rules for achievements. Course builders choose par freely, so achievements measure against a fair
 * par derived from the hole's length: a par 6 on a 30-block hole still counts as par 3.
 */
public final class GolfRules {
	public static final int HOLE_IN_ONE_MIN_LENGTH = 20;
	public static final int SAND_WIZARD_MIN_DISTANCE = 20;
	public static final int LONG_DRIVE_DISTANCE = 70;
	public static final int UNDER_PAR_MIN_HOLES = 9;
	public static final int FULL_ROUND = 18;
	public static final int SNOWMAN = 8;

	private GolfRules() {
	}

	/** Par a hole of this length deserves: driver reaches about 80 blocks, an iron about 50 more. */
	public static int fairPar(int length) {
		if (length <= 15) return 2;
		if (length <= 60) return 3;
		if (length <= 140) return 4;
		return 5;
	}

	public static int effectivePar(int par, int length) {
		return Math.min(par, fairPar(length));
	}

	public static boolean isHoleInOne(int strokes, int length) {
		return strokes == 1 && length >= HOLE_IN_ONE_MIN_LENGTH;
	}

	/** Every hole of an 18-hole round holed out under its fair par. */
	public static boolean isPerfectRound(Scorecard card, UUID player, int[] lengths) {
		if (card.holeCount() != FULL_ROUND) return false;
		for (int h = 0; h < card.holeCount(); h++) {
			if (!card.holedOut(player, h)) return false;
			if (card.strokes(player, h) >= effectivePar(card.par(h), lengths[h])) return false;
		}
		return true;
	}

	/** At least nine scored holes and fewer strokes than their fair par in total. */
	public static boolean isUnderParRound(Scorecard card, UUID player, int[] lengths) {
		int holes = 0;
		int toPar = 0;
		for (int h = 0; h < card.holeCount(); h++) {
			if (card.par(h) == 0) continue; // voided
			holes++;
			toPar += card.strokes(player, h) - effectivePar(card.par(h), lengths[h]);
		}
		return holes >= UNDER_PAR_MIN_HOLES && toPar < 0;
	}

	/** An 18-hole round played to the end, whatever the score. */
	public static boolean isFullEighteen(Scorecard card, UUID player) {
		if (card.holeCount() != FULL_ROUND) return false;
		for (int h = 0; h < card.holeCount(); h++) {
			if (!card.isFinished(player, h)) return false;
		}
		return true;
	}
}
