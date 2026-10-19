package net.colin.minegolf.physics;

/** Position (bottom center of the ball) and velocity, in blocks and blocks per tick. */
public final class BallState {
	public double x, y, z;
	public double vx, vy, vz;
	public boolean onGround;
	public int restTicks;
	public int ticksMoving;

	public boolean resting() {
		return restTicks >= BallPhysics.REST_TICKS;
	}

	public double horizontalSpeed() {
		return Math.sqrt(vx * vx + vz * vz);
	}

	public BallState copy() {
		BallState c = new BallState();
		c.x = x;
		c.y = y;
		c.z = z;
		c.vx = vx;
		c.vy = vy;
		c.vz = vz;
		c.onGround = onGround;
		c.restTicks = restTicks;
		c.ticksMoving = ticksMoving;
		return c;
	}
}
