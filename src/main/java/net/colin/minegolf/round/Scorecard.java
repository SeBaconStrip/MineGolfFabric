package net.colin.minegolf.round;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Strokes per player and hole. A hole is finished for a player once holed or picked up. */
public final class Scorecard {
	private final int[] pars;
	private final Map<UUID, Entry> entries = new LinkedHashMap<>();

	private static final class Entry {
		final String name;
		final int[] strokes;
		final boolean[] finished;
		final boolean[] holed;

		Entry(String name, int holes) {
			this.name = name;
			this.strokes = new int[holes];
			this.finished = new boolean[holes];
			this.holed = new boolean[holes];
		}
	}

	public Scorecard(int[] pars) {
		this.pars = pars.clone();
	}

	public void addPlayer(UUID player, String name) {
		entries.putIfAbsent(player, new Entry(name, pars.length));
	}

	public void removePlayer(UUID player) {
		entries.remove(player);
	}

	public boolean hasPlayer(UUID player) {
		return entries.containsKey(player);
	}

	public List<UUID> players() {
		return new ArrayList<>(entries.keySet());
	}

	public String name(UUID player) {
		return entries.get(player).name;
	}

	public int holeCount() {
		return pars.length;
	}

	public int par(int hole) {
		return pars[hole];
	}

	public void addStroke(UUID player, int hole) {
		Entry e = entries.get(player);
		if (e != null && !e.finished[hole]) e.strokes[hole]++;
	}

	public int strokes(UUID player, int hole) {
		return entries.get(player).strokes[hole];
	}

	/** The ball dropped into the cup. */
	public void finishHole(UUID player, int hole) {
		Entry e = entries.get(player);
		if (e == null || e.finished[hole]) return;
		e.finished[hole] = true;
		e.holed[hole] = true;
	}

	/** Gives up the hole and scores the double-par cap. */
	public void pickup(UUID player, int hole) {
		Entry e = entries.get(player);
		if (e == null) return;
		e.strokes[hole] = cap(hole);
		e.finished[hole] = true;
	}

	/** Removes a hole from scoring (its tee or cup was destroyed during the round). */
	public void voidHole(int hole) {
		pars[hole] = 0;
		for (Entry e : entries.values()) {
			e.strokes[hole] = 0;
			e.finished[hole] = true;
			e.holed[hole] = false;
		}
	}

	public boolean isFinished(UUID player, int hole) {
		Entry e = entries.get(player);
		return e != null && e.finished[hole];
	}

	/** Finished by dropping the ball, not by picking up or a voided hole. */
	public boolean holedOut(UUID player, int hole) {
		Entry e = entries.get(player);
		return e != null && e.holed[hole];
	}

	public boolean reachedCap(UUID player, int hole) {
		return strokes(player, hole) >= cap(hole);
	}

	public boolean holeComplete(int hole) {
		for (Entry e : entries.values()) {
			if (!e.finished[hole]) return false;
		}
		return true;
	}

	public int total(UUID player) {
		int sum = 0;
		for (int s : entries.get(player).strokes) sum += s;
		return sum;
	}

	/** Strokes minus par over the holes this player has finished. */
	public int relativeToPar(UUID player) {
		Entry e = entries.get(player);
		int diff = 0;
		for (int h = 0; h < pars.length; h++) {
			if (e.finished[h]) diff += e.strokes[h] - pars[h];
		}
		return diff;
	}

	private int cap(int hole) {
		return pars[hole] * 2;
	}
}
