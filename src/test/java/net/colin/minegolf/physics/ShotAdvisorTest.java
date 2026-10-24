package net.colin.minegolf.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShotAdvisorTest {
    @Test void wedgeForShortShot() {
        ShotAdvisor.Suggestion s = ShotAdvisor.suggest(20, 0, false, false);
        assertEquals(Club.WEDGE, s.club());
        assertTrue(s.power() > 0.5 && s.power() < 1, "power was " + s.power());
        assertFalse(s.outOfRange());
    }

    @Test void ironForMidShot() {
        assertEquals(Club.IRON, ShotAdvisor.suggest(40, 0, false, false).club());
    }

    @Test void driverOnlyFromTee() {
        assertEquals(Club.DRIVER, ShotAdvisor.suggest(70, 0, true, false).club());
        ShotAdvisor.Suggestion offTee = ShotAdvisor.suggest(70, 0, false, false);
        assertEquals(Club.IRON, offTee.club());
        assertTrue(offTee.outOfRange());
        assertEquals(1.0, offTee.power(), 1e-9);
    }

    @Test void uphillNeedsMorePower() {
        ShotAdvisor.Suggestion flat = ShotAdvisor.suggest(30, 0, false, false);
        ShotAdvisor.Suggestion uphill = ShotAdvisor.suggest(30, 4, false, false);
        assertEquals(flat.club(), uphill.club());
        assertTrue(uphill.power() > flat.power());
    }

    @Test void downhillNeedsLessPower() {
        assertTrue(ShotAdvisor.suggest(30, -4, false, false).power() < ShotAdvisor.suggest(30, 0, false, false).power());
    }

    @Test void putterOnGreen() {
        ShotAdvisor.Suggestion s = ShotAdvisor.suggest(10, 0, false, true);
        assertEquals(Club.PUTTER, s.club());
        assertTrue(s.power() > 0.3 && s.power() < 1, "power was " + s.power());
    }

    @Test void longPuttFallsBackToWedge() {
        assertEquals(Club.WEDGE, ShotAdvisor.suggest(22, 0, false, true).club());
    }

    @Test void suggestedPowerHitsTheTarget() {
        for (double d : new double[] {12, 30, 45, 65}) {
            ShotAdvisor.Suggestion s = ShotAdvisor.suggest(d, 2, true, false);
            assertEquals(d, ShotAdvisor.carry(s.club(), s.power(), 2), 1.0, "distance " + d);
        }
    }

    @Test void targetAboveTheApexIsUnreachable() {
        assertTrue(Double.isNaN(ShotAdvisor.carry(Club.IRON, 0.2, 30)));
    }
}
