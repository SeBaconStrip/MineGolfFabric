package net.colin.minegolf.physics;

/**
 * Golf ball flight and roll on a block grid. The ball is a point at its bottom center.
 * Pure Java so the server tick, the client aim line and the unit tests share one model.
 */
public final class BallPhysics {
	public static final double GRAVITY = 0.05;
	public static final double DRAG = 0.99;
	/** Height the ball rolls over without stopping (carpets, tee mats). */
	public static final double STEP = 0.15;
	public static final double MAX_SUBSTEP = 0.4;
	public static final double REST_SPEED = 0.005;
	public static final int REST_TICKS = 10;
	public static final int STUCK_TICKS = 600;
	public static final double MIN_BOUNCE = 0.08;
	public static final double WALL_KEEP = 0.5;
	public static final double CEILING_KEEP = 0.3;
	public static final double CUP_RADIUS = 0.3;
	public static final double CUP_MAX_SPEED = 0.35;

	public enum Event { NONE, LANDED, AT_REST, HOLED, HAZARD, STUCK }

	private BallPhysics() {
	}

	public static void launch(BallState s, Club club, double power, float yawDegrees, Surface lie) {
		double p = Math.max(0, Math.min(1, power));
		double lieFactor = club == Club.WEDGE && lie == Surface.BUNKER ? 1 : lie.lieModifier;
		double speed = club.maxSpeed() * p * lieFactor;
		double yaw = Math.toRadians(yawDegrees);
		double angle = Math.toRadians(club.launchDegrees());
		double horizontal = speed * Math.cos(angle);
		s.vx = -Math.sin(yaw) * horizontal;
		s.vz = Math.cos(yaw) * horizontal;
		s.vy = speed * Math.sin(angle);
		// A putt never leaves the ground
		s.onGround = club.launchDegrees() == 0 && s.onGround;
		s.restTicks = 0;
		s.ticksMoving = 0;
	}

	public static Event step(BallState s, SurfaceLookup w) {
		if (s.resting()) return Event.NONE;
		if (++s.ticksMoving > STUCK_TICKS) return Event.STUCK;

		if (s.onGround) {
			Surface ground = surfaceUnder(s, w);
			s.vy = 0;
			s.vx *= ground.rollFriction;
			s.vz *= ground.rollFriction;
		} else {
			s.vy -= GRAVITY;
			s.vx *= DRAG;
			s.vy *= DRAG;
			s.vz *= DRAG;
		}

		double speed = Math.sqrt(s.vx * s.vx + s.vy * s.vy + s.vz * s.vz);
		int steps = Math.max(1, (int) Math.ceil(speed / MAX_SUBSTEP));
		Event result = Event.NONE;
		for (int i = 0; i < steps; i++) {
			moveHorizontal(s, w, steps, true);
			moveHorizontal(s, w, steps, false);
			if (s.onGround) {
				if (supportTop(w, s.x, s.y, s.z) < s.y - 1e-6) s.onGround = false;
			} else if (moveVertical(s, w, steps)) {
				result = Event.LANDED;
			}
			if (s.y < w.minY() || w.hazard(floor(s.x), floor(s.y), floor(s.z))) return Event.HAZARD;
			if (s.onGround && inCup(s, w)) return Event.HOLED;
			if (result == Event.LANDED) break;
		}

		if (s.onGround && s.horizontalSpeed() < REST_SPEED) {
			s.vx = 0;
			s.vz = 0;
			if (++s.restTicks >= REST_TICKS) return Event.AT_REST;
		} else {
			s.restTicks = 0;
		}
		return result;
	}

	private static void moveHorizontal(BallState s, SurfaceLookup w, int steps, boolean xAxis) {
		double d = (xAxis ? s.vx : s.vz) / steps;
		if (d == 0) return;
		double nx = xAxis ? s.x + d : s.x;
		double nz = xAxis ? s.z : s.z + d;
		double top = supportTop(w, nx, s.y, nz);
		if (top > s.y + STEP) {
			if (xAxis) s.vx = -s.vx * WALL_KEEP;
			else s.vz = -s.vz * WALL_KEEP;
			return;
		}
		s.x = nx;
		s.z = nz;
		if (s.onGround && top > s.y) s.y = top;
	}

	/** Returns true when the ball touched down this substep. */
	private static boolean moveVertical(BallState s, SurfaceLookup w, int steps) {
		double ny = s.y + s.vy / steps;
		if (s.vy > 0) {
			int headY = floor(ny + 0.2);
			if (headY > floor(s.y) && w.height(floor(s.x), headY, floor(s.z)) > 0) {
				s.vy = -s.vy * CEILING_KEEP;
				return false;
			}
			s.y = ny;
			return false;
		}
		double top = supportTop(w, s.x, ny, s.z);
		if (top < ny || top > s.y + STEP) {
			s.y = ny;
			return false;
		}
		s.y = top;
		Surface ground = surfaceUnder(s, w);
		s.vy = -s.vy * ground.restitution;
		s.vx *= ground.impactKeep;
		s.vz *= ground.impactKeep;
		if (s.vy < MIN_BOUNCE) {
			s.vy = 0;
			s.onGround = true;
		}
		return true;
	}

	private static boolean inCup(BallState s, SurfaceLookup w) {
		int bx = floor(s.x), bz = floor(s.z);
		int by = supportBlockY(w, s.x, s.y, s.z);
		if (by == Integer.MIN_VALUE || !w.isCup(bx, by, bz)) return false;
		double dx = s.x - (bx + 0.5), dz = s.z - (bz + 0.5);
		return dx * dx + dz * dz <= CUP_RADIUS * CUP_RADIUS && s.horizontalSpeed() < CUP_MAX_SPEED;
	}

	public static Surface surfaceUnder(BallState s, SurfaceLookup w) {
		int by = supportBlockY(w, s.x, s.y, s.z);
		return by == Integer.MIN_VALUE ? Surface.ROUGH : w.surface(floor(s.x), by, floor(s.z));
	}

	/** Y of the block whose top is the highest surface at or just above y, or MIN_VALUE if none. */
	static int supportBlockY(SurfaceLookup w, double x, double y, double z) {
		int bx = floor(x), bz = floor(z);
		int by = floor(y + STEP);
		if (w.height(bx, by, bz) > 0) return by;
		if (w.height(bx, by - 1, bz) > 0) return by - 1;
		return Integer.MIN_VALUE;
	}

	static double supportTop(SurfaceLookup w, double x, double y, double z) {
		int by = supportBlockY(w, x, y, z);
		return by == Integer.MIN_VALUE ? Double.NEGATIVE_INFINITY : by + w.height(floor(x), by, floor(z));
	}

	private static int floor(double v) {
		return (int) Math.floor(v);
	}
}
