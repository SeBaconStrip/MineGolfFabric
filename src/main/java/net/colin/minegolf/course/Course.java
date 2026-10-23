package net.colin.minegolf.course;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A named sequence of up to 18 holes. Mutable; changes must be followed by {@link CourseSavedData#setDirty()}. */
public final class Course {
	public static final int MAX_HOLES = 18;
	public static final int MAX_NAME_LENGTH = 32;

	public static final Codec<Course> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(c -> c.id),
			Codec.STRING.fieldOf("name").forGetter(c -> c.name),
			UUIDUtil.CODEC.fieldOf("owner").forGetter(c -> c.owner),
			Hole.CODEC.listOf().fieldOf("holes").forGetter(c -> c.holes)
	).apply(i, Course::new));

	private final UUID id;
	private final String name;
	private final UUID owner;
	private final List<Hole> holes;

	public Course(UUID id, String name, UUID owner, List<Hole> holes) {
		this.id = id;
		this.name = name;
		this.owner = owner;
		this.holes = new ArrayList<>(holes);
	}

	public UUID id() {
		return id;
	}

	public String name() {
		return name;
	}

	public UUID owner() {
		return owner;
	}

	public List<Hole> holes() {
		return holes;
	}

	public boolean isFull() {
		return holes.size() >= MAX_HOLES;
	}

	/** True if the block is already the tee or cup of one of the holes, not counting the hole at ignore. */
	public boolean uses(BlockPos pos, int ignore) {
		for (int i = 0; i < holes.size(); i++) {
			Hole h = holes.get(i);
			if (i != ignore && (h.tee().equals(pos) || h.cup().equals(pos))) return true;
		}
		return false;
	}

	public void addHole(Hole hole) {
		if (!isFull()) holes.add(hole);
	}

	public void changePar(int index, int delta) {
		if (index >= 0 && index < holes.size()) holes.set(index, holes.get(index).withPar(holes.get(index).par() + delta));
	}

	public void removeLastHole() {
		if (!holes.isEmpty()) holes.removeLast();
	}

	/** Swaps the hole with its neighbour delta places away; does nothing past either end. */
	public void moveHole(int index, int delta) {
		int other = index + delta;
		if (index >= 0 && index < holes.size() && other >= 0 && other < holes.size()) {
			holes.set(index, holes.set(other, holes.get(index)));
		}
	}

	public void removeHole(int index) {
		if (index >= 0 && index < holes.size()) holes.remove(index);
	}

	/** Puts a relinked hole in place of the old one at the same position in the order. */
	public void setHole(int index, Hole hole) {
		if (index >= 0 && index < holes.size()) holes.set(index, hole);
	}
}
