package net.colin.minegolf.physics;

/** The world as the ball physics sees it. Block coordinates. */
public interface SurfaceLookup {
	/** Collision top of the block in [0, 1]; 0 means the ball passes through. */
	double height(int x, int y, int z);

	/** Surface type of a block the ball rests on. */
	Surface surface(int x, int y, int z);

	/** Water, lava or anything else that costs a penalty stroke. */
	boolean hazard(int x, int y, int z);

	boolean isCup(int x, int y, int z);

	int minY();
}
