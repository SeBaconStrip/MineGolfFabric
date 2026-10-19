package net.colin.minegolf.round;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ScorecardTest {
    final UUID a = UUID.randomUUID(), b = UUID.randomUUID();

    @Test void totalsAndRelativeToPar() {
        Scorecard c = new Scorecard(new int[]{3, 4});
        c.addPlayer(a, "A");
        for (int i = 0; i < 4; i++) c.addStroke(a, 0);
        c.finishHole(a, 0);
        for (int i = 0; i < 3; i++) c.addStroke(a, 1);
        c.finishHole(a, 1);
        assertEquals(7, c.total(a));
        assertEquals(0, c.relativeToPar(a));
    }

    @Test void pickupScoresDoublePar() {
        Scorecard c = new Scorecard(new int[]{3});
        c.addPlayer(a, "A");
        c.addStroke(a, 0);
        c.pickup(a, 0);
        assertEquals(6, c.strokes(a, 0));
        assertTrue(c.isFinished(a, 0));
    }

    @Test void capReachedAtDoublePar() {
        Scorecard c = new Scorecard(new int[]{2});
        c.addPlayer(a, "A");
        for (int i = 0; i < 3; i++) c.addStroke(a, 0);
        assertFalse(c.reachedCap(a, 0));
        c.addStroke(a, 0);
        assertTrue(c.reachedCap(a, 0));
    }

    @Test void holeCompleteOnlyWhenEveryoneFinished() {
        Scorecard c = new Scorecard(new int[]{3});
        c.addPlayer(a, "A");
        c.addPlayer(b, "B");
        c.addStroke(a, 0);
        c.finishHole(a, 0);
        assertFalse(c.holeComplete(0));
        c.pickup(b, 0);
        assertTrue(c.holeComplete(0));
    }

    @Test void removedPlayerNoLongerBlocksHole() {
        Scorecard c = new Scorecard(new int[]{3});
        c.addPlayer(a, "A");
        c.addPlayer(b, "B");
        c.finishHole(a, 0);
        c.removePlayer(b);
        assertTrue(c.holeComplete(0));
    }
}
