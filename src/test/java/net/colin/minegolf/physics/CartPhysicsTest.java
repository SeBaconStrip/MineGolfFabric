package net.colin.minegolf.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CartPhysicsTest {
    static final CartPhysics.Input GAS = new CartPhysics.Input(true, false, false, false);
    static final CartPhysics.Input BACK = new CartPhysics.Input(false, true, false, false);

    static CartPhysics.State run(CartPhysics.State s, CartPhysics.Input in, int ticks) {
        for (int i = 0; i < ticks; i++) CartPhysics.step(s, in);
        return s;
    }

    @Test void reachesTopSpeedAndStaysThere() {
        CartPhysics.State s = new CartPhysics.State();
        double max = 0;
        for (int i = 0; i < 200; i++) {
            CartPhysics.step(s, GAS);
            max = Math.max(max, s.speed);
        }
        assertEquals(CartPhysics.MAX_SPEED, s.speed, 1e-9);
        assertTrue(max <= CartPhysics.MAX_SPEED + 1e-9);
    }

    @Test void reachesTopSpeedWithinTwoSeconds() {
        assertEquals(CartPhysics.MAX_SPEED, run(new CartPhysics.State(), GAS, 40).speed, 1e-9);
    }

    @Test void brakesThenReversesSlowly() {
        CartPhysics.State s = run(new CartPhysics.State(), GAS, 60);
        CartPhysics.step(s, BACK);
        assertTrue(s.speed > 0 && s.speed < CartPhysics.MAX_SPEED, "first it brakes");
        run(s, BACK, 200);
        assertEquals(-CartPhysics.MAX_REVERSE, s.speed, 1e-9);
    }

    @Test void coastsToAStop() {
        CartPhysics.State s = run(new CartPhysics.State(), GAS, 60);
        double distance = 0;
        for (int i = 0; i < 200; i++) {
            CartPhysics.step(s, CartPhysics.Input.NONE);
            distance += s.speed;
        }
        assertEquals(0, s.speed);
        assertTrue(distance > 3 && distance < 12, "coasted " + distance);
    }

    @Test void noTurningAtRest() {
        CartPhysics.State s = run(new CartPhysics.State(), new CartPhysics.Input(false, false, true, false), 20);
        assertEquals(0F, s.yaw);
    }

    @Test void turnsRightWhileMoving() {
        CartPhysics.State s = run(new CartPhysics.State(), GAS, 30);
        run(s, new CartPhysics.Input(true, false, false, true), 10);
        assertEquals(10 * CartPhysics.MAX_TURN, s.yaw, 1e-4);
    }

    @Test void reverseSteeringIsMirrored() {
        CartPhysics.State s = run(new CartPhysics.State(), BACK, 60);
        run(s, new CartPhysics.Input(false, true, false, true), 5);
        assertTrue(s.yaw < 0, "steering right while reversing swings the nose left, yaw " + s.yaw);
    }

    @Test void slowCartTurnsLess() {
        CartPhysics.State slow = run(new CartPhysics.State(), GAS, 2);
        float before = slow.yaw;
        CartPhysics.step(slow, new CartPhysics.Input(false, false, false, true));
        assertTrue(slow.yaw - before < CartPhysics.MAX_TURN);
    }
}
