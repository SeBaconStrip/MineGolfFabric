package net.colin.minegolf.physics;

/**
 * Picks a club and power for a target, by flying the ball with {@link BallPhysics} in an empty world.
 * Counts carry only, so the ball rolls on a little after landing.
 */
public final class ShotAdvisor {
	/** Longest putt the advisor suggests; beyond this a chip is the better shot. */
	public static final double MAX_PUTT = 15;
	private static final int SEARCH_STEPS = 30;
	private static final int MAX_TICKS = 600;

	/** The club to use and how hard to hit it, in the same 0–1 scale as the power bar. */
	public record Suggestion(Club club, double power, boolean outOfRange) {
	}

	private ShotAdvisor() {
	}

	/**
	 * @param distance horizontal blocks to the target
	 * @param rise     target height minus the ball's height
	 * @param onTee    whether the driver may be used
	 * @param greenTarget whether the target lies on the green, so a putt can reach it
	 */
	public static Suggestion suggest(double distance, double rise, boolean onTee, boolean greenTarget) {
		if (greenTarget && distance <= MAX_PUTT && roll(1) >= distance) {
			return new Suggestion(Club.PUTTER, search(p -> roll(p), distance), false);
		}
		Club[] clubs = onTee ? new Club[] {Club.WEDGE, Club.IRON, Club.DRIVER} : new Club[] {Club.WEDGE, Club.IRON};
		for (Club club : clubs) {
			double full = carry(club, 1, rise);
			if (!Double.isNaN(full) && full >= distance) {
				return new Suggestion(club, search(p -> carryOrZero(club, p, rise), distance), false);
			}
		}
		return new Suggestion(clubs[clubs.length - 1], 1, true);
	}

	/** Horizontal distance at which the ball comes down through height rise, or NaN if it never reaches it. */
	public static double carry(Club club, double power, double rise) {
		BallState s = new BallState();
		s.onGround = true;
		BallPhysics.launch(s, club, power, 0f, Surface.FAIRWAY);
		SurfaceLookup air = new Empty();
		double prevY = s.y, prevZ = s.z;
		for (int i = 0; i < MAX_TICKS; i++) {
			BallPhysics.step(s, air);
			if (s.vy < 0 && s.y <= rise) {
				if (prevY <= rise) return Double.NaN;
				double t = (prevY - rise) / (prevY - s.y);
				return prevZ + (s.z - prevZ) * t;
			}
			prevY = s.y;
			prevZ = s.z;
		}
		return Double.NaN;
	}

	private static double carryOrZero(Club club, double power, double rise) {
		double c = carry(club, power, rise);
		return Double.isNaN(c) ? 0 : c;
	}

	/** How far a putt rolls on a flat green. */
	static double roll(double power) {
		BallState s = new BallState();
		s.onGround = true;
		BallPhysics.launch(s, Club.PUTTER, power, 0f, Surface.GREEN);
		SurfaceLookup green = new Green();
		for (int i = 0; i < MAX_TICKS * 2; i++) {
			if (BallPhysics.step(s, green) == BallPhysics.Event.AT_REST) break;
		}
		return s.z;
	}

	/** Power in [0, 1] where the rising distance function reaches the target. */
	private static double search(java.util.function.DoubleUnaryOperator distanceAt, double target) {
		double lo = 0, hi = 1;
		for (int i = 0; i < SEARCH_STEPS; i++) {
			double mid = (lo + hi) / 2;
			if (distanceAt.applyAsDouble(mid) < target) lo = mid;
			else hi = mid;
		}
		return hi;
	}

	/** Nothing to hit, nothing to land on. */
	private static final class Empty implements SurfaceLookup {
		public double height(int x, int y, int z) { return 0; }
		public Surface surface(int x, int y, int z) { return Surface.FAIRWAY; }
		public boolean hazard(int x, int y, int z) { return false; }
		public boolean isCup(int x, int y, int z) { return false; }
		public int minY() { return -1000; }
	}

	/** A flat green with its surface at y = 0. */
	private static final class Green implements SurfaceLookup {
		public double height(int x, int y, int z) { return y < 0 ? 1 : 0; }
		public Surface surface(int x, int y, int z) { return Surface.GREEN; }
		public boolean hazard(int x, int y, int z) { return false; }
		public boolean isCup(int x, int y, int z) { return false; }
		public int minY() { return -64; }
	}
}
