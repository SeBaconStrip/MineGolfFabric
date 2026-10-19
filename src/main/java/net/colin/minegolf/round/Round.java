package net.colin.minegolf.round;

import net.colin.minegolf.course.Hole;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One group playing a course. All players play the same hole at the same time. */
public final class Round {
	final int id;
	final String courseName;
	final List<Hole> holes;
	final Scorecard card;
	/** Ball entity per player for the current hole. */
	final Map<UUID, UUID> balls = new HashMap<>();
	int currentHole;
	boolean joinOpen = true;

	Round(int id, String courseName, List<Hole> holes) {
		this.id = id;
		this.courseName = courseName;
		this.holes = List.copyOf(holes);
		int[] pars = new int[holes.size()];
		for (int i = 0; i < pars.length; i++) pars[i] = holes.get(i).par();
		this.card = new Scorecard(pars);
	}

	public int id() {
		return id;
	}

	public Hole hole() {
		return holes.get(currentHole);
	}

	public boolean isOver() {
		return currentHole >= holes.size();
	}
}
