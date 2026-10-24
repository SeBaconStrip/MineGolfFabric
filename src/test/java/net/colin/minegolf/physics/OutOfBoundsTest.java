package net.colin.minegolf.physics;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OutOfBoundsTest {
    /** Stakes along x at the given z, from x0 in steps of gap. */
    static List<double[]> row(double z, double x0, double gap, int count) {
        List<double[]> stakes = new ArrayList<>();
        for (int i = 0; i < count; i++) stakes.add(new double[] {x0 + i * gap, z});
        return stakes;
    }

    @Test void rowOfFourMakesThreeLines() {
        assertEquals(3, OutOfBounds.lines(row(10, 0, 8, 4)).size());
    }

    @Test void ballBehindTheRowIsOut() {
        List<OutOfBounds.Line> lines = OutOfBounds.lines(row(10, 0, 8, 4));
        assertTrue(OutOfBounds.isOut(12, 0, 12, 20, lines));
        assertFalse(OutOfBounds.isOut(12, 0, 12, 8, lines), "stopped in front of the line");
    }

    @Test void crossingTwoRowsIsIn() {
        List<double[]> stakes = row(10, 0, 8, 4);
        stakes.addAll(row(40, 0, 8, 4));
        assertTrue(OutOfBounds.isOut(12, 0, 12, 20, OutOfBounds.lines(stakes)));
        assertFalse(OutOfBounds.isOut(12, 0, 12, 50, OutOfBounds.lines(stakes)));
    }

    @Test void passingTheEndOfTheRowIsIn() {
        assertFalse(OutOfBounds.isOut(30, 0, 30, 20, OutOfBounds.lines(row(10, 0, 8, 4))));
    }

    @Test void stakesTooFarApartMakeNoLine() {
        List<OutOfBounds.Line> lines = OutOfBounds.lines(row(10, 0, 20, 3));
        assertTrue(lines.isEmpty());
        assertFalse(OutOfBounds.isOut(10, 0, 10, 20, lines));
    }

    @Test void stakeLinksOnlyItsTwoClosest() {
        // Centre stake with neighbours at 4, 5 and 6 blocks: it picks 4 and 5, the third links only to its own two
        List<double[]> stakes = List.of(new double[] {0, 0}, new double[] {4, 0}, new double[] {0, 5}, new double[] {-6, 0});
        List<OutOfBounds.Line> lines = OutOfBounds.lines(stakes);
        assertTrue(lines.stream().noneMatch(l -> l.x0() == 4 && l.x1() == -6 || l.x0() == -6 && l.x1() == 4));
        assertTrue(lines.stream().anyMatch(l -> (l.x0() == 0 && l.z0() == 0 && l.x1() == -6) || (l.x1() == 0 && l.z1() == 0 && l.x0() == -6)),
                "the far stake still links to the centre because it chose it");
    }

    @Test void cornerIsClosed() {
        // Fairway corner z < 10, x < 24: stakes along z=10 up to x=24, then down along x=24
        List<double[]> stakes = row(10, 0, 8, 4);
        stakes.add(new double[] {24, 2});
        stakes.add(new double[] {24, -6});
        List<OutOfBounds.Line> lines = OutOfBounds.lines(stakes);
        assertTrue(OutOfBounds.isOut(10, 0, 34, 14, lines), "diagonal just past the corner");
        assertTrue(OutOfBounds.isOut(10, 0, 30, 0, lines), "across the side");
        assertFalse(OutOfBounds.isOut(10, 0, 20, 5, lines), "stays inside");
    }

    @Test void shotThroughAStakeCountsOnce() {
        assertTrue(OutOfBounds.isOut(8, 0, 8, 20, OutOfBounds.lines(row(10, 0, 8, 4))));
    }
}
