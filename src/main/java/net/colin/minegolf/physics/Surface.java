package net.colin.minegolf.physics;

/** How a ground type treats the ball. */
public enum Surface {
	GREEN(0.20, 0.985, 0.70, 1.0),
	FAIRWAY(0.35, 0.92, 0.75, 1.0),
	ROUGH(0.15, 0.80, 0.50, 0.75),
	BUNKER(0.0, 0.60, 0.10, 0.40),
	ICE(0.30, 0.995, 0.95, 1.0);

	/** Share of vertical speed kept when bouncing. */
	public final double restitution;
	/** Horizontal speed multiplier per tick while rolling. */
	public final double rollFriction;
	/** Horizontal speed multiplier when the ball lands. */
	public final double impactKeep;
	/** Shot power multiplier when hitting from this surface. */
	public final double lieModifier;

	Surface(double restitution, double rollFriction, double impactKeep, double lieModifier) {
		this.restitution = restitution;
		this.rollFriction = rollFriction;
		this.impactKeep = impactKeep;
		this.lieModifier = lieModifier;
	}
}
