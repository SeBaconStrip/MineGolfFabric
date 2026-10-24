package net.colin.minegolf.physics;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Out-of-bounds lines between stakes, seen from above. Each stake links to its two closest stakes within
 * {@link #MAX_GAP}; a ball is out when the way from where it was hit to where it stopped crosses the lines an odd
 * number of times.
 */
public final class OutOfBounds {
	public static final double MAX_GAP = 16;
	private static final int LINKS_PER_STAKE = 2;
	private static final double BETWEEN_RADIUS = 1.0;

	/** A line seen from above; y0 and y1 are only carried along for drawing it. */
	public record Line(double x0, double z0, double x1, double z1, double y0, double y1) {
	}

	private OutOfBounds() {
	}

	/** Lines between stakes given as {x, z} or {x, z, y}. */
	public static List<Line> lines(List<double[]> stakes) {
		Set<Long> edges = new LinkedHashSet<>();
		for (int i = 0; i < stakes.size(); i++) {
			int[] closest = new int[LINKS_PER_STAKE];
			double[] best = new double[LINKS_PER_STAKE];
			java.util.Arrays.fill(closest, -1);
			java.util.Arrays.fill(best, MAX_GAP * MAX_GAP);
			for (int j = 0; j < stakes.size(); j++) {
				if (j == i) continue;
				double d = dist2(stakes.get(i), stakes.get(j));
				if (d == 0 || d > best[LINKS_PER_STAKE - 1] || stakeBetween(stakes, i, j)) continue;
				int k = LINKS_PER_STAKE - 1;
				while (k > 0 && d < best[k - 1]) {
					best[k] = best[k - 1];
					closest[k] = closest[k - 1];
					k--;
				}
				best[k] = d;
				closest[k] = j;
			}
			for (int j : closest) {
				if (j >= 0) edges.add(((long) Math.min(i, j) << 32) | Math.max(i, j));
			}
		}
		List<Line> lines = new ArrayList<>();
		for (long e : edges) {
			double[] a = stakes.get((int) (e >>> 32));
			double[] b = stakes.get((int) e);
			lines.add(new Line(a[0], a[1], b[0], b[1], a.length > 2 ? a[2] : 0, b.length > 2 ? b[2] : 0));
		}
		return lines;
	}

	/** True if the way from (x0, z0) to (x1, z1) crosses the lines an odd number of times. */
	public static boolean isOut(double x0, double z0, double x1, double z1, List<Line> lines) {
		int crossings = 0;
		for (Line l : lines) {
			if (crosses(x0, z0, x1, z1, l)) crossings++;
		}
		return crossings % 2 == 1;
	}

	private static boolean crosses(double ax, double az, double bx, double bz, Line l) {
		// Half-open on the line's ends, so a shot through a stake shared by two lines counts once
		boolean c = side(ax, az, bx, bz, l.x0, l.z0) > 0;
		boolean d = side(ax, az, bx, bz, l.x1, l.z1) > 0;
		if (c == d) return false;
		double sa = side(l.x0, l.z0, l.x1, l.z1, ax, az);
		double sb = side(l.x0, l.z0, l.x1, l.z1, bx, bz);
		return sa * sb < 0;
	}

	private static double side(double ax, double az, double bx, double bz, double px, double pz) {
		return (bx - ax) * (pz - az) - (bz - az) * (px - ax);
	}

	/** A line never jumps over another stake, otherwise rows would get doubled lines that cancel out. */
	private static boolean stakeBetween(List<double[]> stakes, int i, int j) {
		double[] a = stakes.get(i), b = stakes.get(j);
		double dx = b[0] - a[0], dz = b[1] - a[1];
		double len2 = dx * dx + dz * dz;
		for (int k = 0; k < stakes.size(); k++) {
			if (k == i || k == j) continue;
			double[] p = stakes.get(k);
			double t = ((p[0] - a[0]) * dx + (p[1] - a[1]) * dz) / len2;
			if (t <= 0 || t >= 1) continue;
			double ox = a[0] + t * dx - p[0], oz = a[1] + t * dz - p[1];
			if (ox * ox + oz * oz < BETWEEN_RADIUS * BETWEEN_RADIUS) return true;
		}
		return false;
	}

	private static double dist2(double[] a, double[] b) {
		double dx = a[0] - b[0], dz = a[1] - b[1];
		return dx * dx + dz * dz;
	}
}
