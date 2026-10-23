package net.colin.minegolf.round;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GolfRulesTest {
	private static final UUID P = UUID.randomUUID();

	/** A card where player P holed every hole with the given strokes. */
	private static Scorecard card(int[] pars, int[] strokes) {
		Scorecard card = new Scorecard(pars);
		card.addPlayer(P, "p");
		for (int h = 0; h < pars.length; h++) {
			for (int s = 0; s < strokes[h]; s++) card.addStroke(P, h);
			card.finishHole(P, h);
		}
		return card;
	}

	private static int[] filled(int holes, int value) {
		int[] a = new int[holes];
		Arrays.fill(a, value);
		return a;
	}

	@Test
	void fairParFollowsLength() {
		assertEquals(2, GolfRules.fairPar(10));
		assertEquals(3, GolfRules.fairPar(60));
		assertEquals(4, GolfRules.fairPar(61));
		assertEquals(4, GolfRules.fairPar(140));
		assertEquals(5, GolfRules.fairPar(141));
	}

	@Test
	void inflatedParIsCappedForAchievements() {
		// Par 6 on a 30-block hole counts as par 3
		assertEquals(3, GolfRules.effectivePar(6, 30));
		// A harder par than the length suggests is kept
		assertEquals(3, GolfRules.effectivePar(3, 100));
	}

	@Test
	void holeInOneNeedsMinimumLength() {
		assertTrue(GolfRules.isHoleInOne(1, 20));
		assertFalse(GolfRules.isHoleInOne(1, 19));
		assertFalse(GolfRules.isHoleInOne(2, 50));
	}

	@Test
	void perfectRoundNeedsEighteenHolesAllUnderPar() {
		int[] lengths = filled(18, 100); // fair par 4
		Scorecard perfect = card(filled(18, 4), filled(18, 3));
		assertTrue(GolfRules.isPerfectRound(perfect, P, lengths));

		int[] oneParHole = filled(18, 3);
		oneParHole[17] = 4;
		assertFalse(GolfRules.isPerfectRound(card(filled(18, 4), oneParHole), P, lengths));

		// Only 9 holes
		assertFalse(GolfRules.isPerfectRound(card(filled(9, 4), filled(9, 3)), P, filled(9, 100)));
	}

	@Test
	void perfectRoundIgnoresInflatedPar() {
		// Par 6 everywhere on 30-block holes: 4 strokes is over the fair par 3
		assertFalse(GolfRules.isPerfectRound(card(filled(18, 6), filled(18, 4)), P, filled(18, 30)));
	}

	@Test
	void pickedUpOrVoidedHoleBreaksPerfectRound() {
		Scorecard card = card(filled(18, 4), filled(18, 3));
		card.voidHole(5);
		assertFalse(GolfRules.isPerfectRound(card, P, filled(18, 100)));
	}

	@Test
	void underParRoundNeedsNineHoles() {
		assertTrue(GolfRules.isUnderParRound(card(filled(9, 4), filled(9, 4 - 1)), P, filled(9, 100)));
		assertFalse(GolfRules.isUnderParRound(card(filled(8, 4), filled(8, 3)), P, filled(8, 100)));
		// Even with par
		assertFalse(GolfRules.isUnderParRound(card(filled(9, 4), filled(9, 4)), P, filled(9, 100)));
	}

	@Test
	void fullEighteenCountsEveryFinishedHole() {
		assertTrue(GolfRules.isFullEighteen(card(filled(18, 4), filled(18, 7)), P));
		assertFalse(GolfRules.isFullEighteen(card(filled(17, 4), filled(17, 4)), P));
	}

	@Test
	void holedOutIsFalseAfterPickup() {
		Scorecard card = new Scorecard(new int[] {4});
		card.addPlayer(P, "p");
		card.pickup(P, 0);
		assertFalse(card.holedOut(P, 0));
	}
}
