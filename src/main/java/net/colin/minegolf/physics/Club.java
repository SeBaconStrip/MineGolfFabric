package net.colin.minegolf.physics;

/** Club profiles. Speeds are calibrated so a full shot carries the distance in the spec. */
public enum Club {
	DRIVER(3.48, 12),
	IRON(2.03, 25),
	WEDGE(1.28, 50),
	PUTTER(0.23, 0);

	public static final int CHARGE_TICKS = 30;
	public static final int OVERCHARGE_TICKS = 60;

	private final double maxSpeed;
	private final double launchDegrees;

	Club(double maxSpeed, double launchDegrees) {
		this.maxSpeed = maxSpeed;
		this.launchDegrees = launchDegrees;
	}

	public double maxSpeed() {
		return maxSpeed;
	}

	public double launchDegrees() {
		return launchDegrees;
	}

	/** Power for how long use was held: ramps up over 1.5 s, holds until 3 s, then drops to nothing. */
	public static double powerForTicks(int ticks) {
		if (ticks <= 0) return 0;
		if (ticks <= CHARGE_TICKS) return ticks / (double) CHARGE_TICKS;
		if (ticks <= OVERCHARGE_TICKS) return 1;
		return 0;
	}
}
