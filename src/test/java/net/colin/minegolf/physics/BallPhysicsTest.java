package net.colin.minegolf.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BallPhysicsTest {
    /** Flat world: ground block top at y=0 of the given surface, optional cup and water columns. */
    static SurfaceLookup flat(Surface s, int cupX, int cupZ, int waterX) {
        return new SurfaceLookup() {
            public double height(int x, int y, int z) { return y == -1 ? 1.0 : (y < -1 ? 1.0 : 0.0); }
            public Surface surface(int x, int y, int z) { return s; }
            public boolean hazard(int x, int y, int z) { return x == waterX && y == 0; }
            public boolean isCup(int x, int y, int z) { return x == cupX && z == cupZ && y == -1; }
            public int minY() { return -64; }
        };
    }

    static BallState ballAt(double x, double z) {
        BallState s = new BallState();
        s.x = x; s.y = 0; s.z = z; s.onGround = true;
        return s;
    }

    static BallPhysics.Event runUntilDone(BallState s, SurfaceLookup w) {
        for (int i = 0; i < 2000; i++) {
            BallPhysics.Event e = BallPhysics.step(s, w);
            if (e != BallPhysics.Event.NONE && e != BallPhysics.Event.LANDED) return e;
        }
        return BallPhysics.Event.NONE;
    }

    static double carry(Club club, Surface ground) {
        BallState s = ballAt(0.5, 0.5);
        BallPhysics.launch(s, club, 1.0, 0f, Surface.FAIRWAY);
        SurfaceLookup w = flat(ground, 9999, 9999, 9999);
        for (int i = 0; i < 400; i++) {
            if (BallPhysics.step(s, w) == BallPhysics.Event.LANDED) return s.z - 0.5;
        }
        return -1;
    }

    @Test void driverCarriesAbout80() { assertEquals(80, carry(Club.DRIVER, Surface.FAIRWAY), 12); }
    @Test void ironCarriesAbout50() { assertEquals(50, carry(Club.IRON, Surface.FAIRWAY), 7.5); }
    @Test void wedgeCarriesAbout25() { assertEquals(25, carry(Club.WEDGE, Surface.FAIRWAY), 4); }

    @Test void putterRollsAbout15OnGreen() {
        BallState s = ballAt(0.5, 0.5);
        BallPhysics.launch(s, Club.PUTTER, 1.0, 0f, Surface.GREEN);
        assertEquals(BallPhysics.Event.AT_REST, runUntilDone(s, flat(Surface.GREEN, 9999, 9999, 9999)));
        assertEquals(15, s.z - 0.5, 2.5);
        assertTrue(s.resting());
    }

    @Test void bunkerPlugsBall() {
        BallState s = ballAt(0.5, 0.5);
        BallPhysics.launch(s, Club.IRON, 1.0, 0f, Surface.FAIRWAY);
        SurfaceLookup w = flat(Surface.BUNKER, 9999, 9999, 9999);
        double landedAt = -1;
        for (int i = 0; i < 400 && landedAt < 0; i++) if (BallPhysics.step(s, w) == BallPhysics.Event.LANDED) landedAt = s.z;
        assertEquals(BallPhysics.Event.AT_REST, runUntilDone(s, w));
        assertTrue(s.z - landedAt < 3, "ball should stop within 3 blocks of landing in a bunker");
    }

    @Test void slowPuttDropsIntoCup() {
        BallState s = ballAt(0.5, 0.5);
        BallPhysics.launch(s, Club.PUTTER, 0.35, 0f, Surface.GREEN); // ~5 blocks
        assertEquals(BallPhysics.Event.HOLED, runUntilDone(s, flat(Surface.GREEN, 0, 3, 9999)));
    }

    @Test void fastBallLipsOut() {
        BallState s = ballAt(0.5, 0.5);
        s.vz = 0.6; s.onGround = true; // too fast for the cup at z=2
        BallPhysics.Event e = runUntilDone(s, flat(Surface.ICE, 0, 2, 9999));
        assertNotEquals(BallPhysics.Event.HOLED, e);
    }

    @Test void waterIsHazard() {
        BallState s = ballAt(0.5, 0.5);
        s.vx = 0.3; s.onGround = true;
        assertEquals(BallPhysics.Event.HAZARD, runUntilDone(s, flat(Surface.ICE, 9999, 9999, 3)));
    }

    @Test void wallReflectsWithoutTunneling() {
        SurfaceLookup wall = new SurfaceLookup() {
            public double height(int x, int y, int z) { return y < 0 || (z == 5 && y < 3) ? 1.0 : 0.0; }
            public Surface surface(int x, int y, int z) { return Surface.GREEN; }
            public boolean hazard(int x, int y, int z) { return false; }
            public boolean isCup(int x, int y, int z) { return false; }
            public int minY() { return -64; }
        };
        BallState s = ballAt(0.5, 0.5);
        s.vz = 3.0; s.onGround = true;
        runUntilDone(s, wall);
        assertTrue(s.z < 5, "ball must stay in front of the wall, was " + s.z);
    }

    @Test void stepsUpOntoCarpetHeightBlocks() {
        SurfaceLookup mat = new SurfaceLookup() {
            public double height(int x, int y, int z) { return y < 0 ? 1.0 : (y == 0 && z >= 3 ? 0.0625 : 0.0); }
            public Surface surface(int x, int y, int z) { return Surface.GREEN; }
            public boolean hazard(int x, int y, int z) { return false; }
            public boolean isCup(int x, int y, int z) { return false; }
            public int minY() { return -64; }
        };
        BallState s = ballAt(0.5, 0.5);
        s.vz = 0.2; s.onGround = true;
        runUntilDone(s, mat);
        assertTrue(s.z > 4, "ball should roll over the mat");
        assertEquals(0.0625, s.y, 1e-6);
    }

    /** Fairway with a one-block terrace from z=5 on. */
    static SurfaceLookup terrace() {
        return new SurfaceLookup() {
            public double height(int x, int y, int z) { return y < 0 || (y == 0 && z >= 5) ? 1.0 : 0.0; }
            public Surface surface(int x, int y, int z) { return Surface.FAIRWAY; }
            public boolean hazard(int x, int y, int z) { return false; }
            public boolean isCup(int x, int y, int z) { return false; }
            public int minY() { return -64; }
        };
    }

    @Test void fastBallRollsUpOneBlockStep() {
        BallState s = ballAt(0.5, 0.5);
        s.vz = 0.8; s.onGround = true;
        runUntilDone(s, terrace());
        assertEquals(1.0, s.y, 1e-6, "ball should be on the upper terrace");
        assertTrue(s.z > 5.5);
    }

    @Test void slowBallRollsBackFromStep() {
        BallState s = ballAt(0.5, 0.5);
        s.vz = 0.25; s.onGround = true;
        runUntilDone(s, terrace());
        assertEquals(0.0, s.y, 1e-6);
        assertTrue(s.z < 5);
    }

    @Test void climbingCostsSpeed() {
        BallState flatBall = ballAt(0.5, 0.5);
        flatBall.vz = 0.8; flatBall.onGround = true;
        runUntilDone(flatBall, flat(Surface.FAIRWAY, 9999, 9999, 9999));
        BallState uphill = ballAt(0.5, 0.5);
        uphill.vz = 0.8; uphill.onGround = true;
        runUntilDone(uphill, terrace());
        assertTrue(uphill.z < flatBall.z - 1, "uphill roll should be shorter");
    }

    @Test void powerRampsHoldsThenDrops() {
        assertEquals(0, Club.powerForTicks(0), 1e-9);
        assertEquals(0.5, Club.powerForTicks(15), 1e-9);
        assertEquals(1, Club.powerForTicks(30), 1e-9);
        assertEquals(1, Club.powerForTicks(59), 1e-9);
        assertEquals(0, Club.powerForTicks(61), 1e-9);
    }
}
