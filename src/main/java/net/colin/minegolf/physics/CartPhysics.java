package net.colin.minegolf.physics;

/** Speed and steering of the golf cart, one call per tick. Speed in blocks per tick, negative when reversing. */
public final class CartPhysics {
	public static final double MAX_SPEED = 0.35;
	public static final double MAX_REVERSE = 0.125;
	public static final double ACCELERATION = 0.012;
	public static final double REVERSE_ACCELERATION = 0.006;
	public static final double BRAKE = 0.03;
	/** Share of speed kept per tick while coasting. */
	public static final double FRICTION = 0.95;
	public static final double STOP_SPEED = 0.002;
	/** Degrees per tick at full steering speed. */
	public static final float MAX_TURN = 5F;
	/** Speed from which the cart steers at full rate; slower carts turn less. */
	private static final double FULL_STEER_SPEED = 0.15;

	public static final class State {
		public double speed;
		/** Degrees, like an entity's yaw. */
		public float yaw;
	}

	public record Input(boolean forward, boolean back, boolean left, boolean right) {
		public static final Input NONE = new Input(false, false, false, false);
	}

	private CartPhysics() {
	}

	public static void step(State s, Input in) {
		boolean gas = in.forward && !in.back;
		boolean reverse = in.back && !in.forward;
		if (gas) {
			s.speed = s.speed < 0 ? Math.min(0, s.speed + BRAKE) : Math.min(MAX_SPEED, s.speed + ACCELERATION);
		} else if (reverse) {
			s.speed = s.speed > 0 ? Math.max(0, s.speed - BRAKE) : Math.max(-MAX_REVERSE, s.speed - REVERSE_ACCELERATION);
		} else {
			s.speed *= FRICTION;
			if (Math.abs(s.speed) < STOP_SPEED) s.speed = 0;
		}

		int steer = (in.right ? 1 : 0) - (in.left ? 1 : 0);
		if (steer != 0 && s.speed != 0) {
			float rate = (float) (MAX_TURN * Math.min(1, Math.abs(s.speed) / FULL_STEER_SPEED));
			// Turning right raises the yaw; reversing swings the nose the other way, like a car
			s.yaw += steer * rate * (float) Math.signum(s.speed);
		}
	}
}
