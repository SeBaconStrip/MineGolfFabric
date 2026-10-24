package net.colin.minegolf.course;

import net.colin.minegolf.block.HoleSignBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;

import java.util.Collection;
import java.util.List;

/** Fills hole signs from the course data: the closest linked tee box within reach decides the hole. */
public final class HoleSigns {
	public static final int REACH = 4;
	public static final int VERTICAL_REACH = 2;
	private static final int NAME_LENGTH = 15;

	private HoleSigns() {
	}

	/** Writes the sign at pos and locks it against editing. */
	public static void write(ServerLevel level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) return;
		SignText text = textFor(level, pos);
		sign.setWaxed(true);
		sign.setText(text, SignTextSlot.FRONT);
		sign.setText(text, SignTextSlot.BACK);
	}

	/** Rewrites every hole sign within reach of these tees, after holes were changed. */
	public static void refreshAround(ServerLevel level, Collection<BlockPos> tees) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (BlockPos tee : tees) {
			for (int dx = -REACH; dx <= REACH; dx++) {
				for (int dy = -VERTICAL_REACH; dy <= VERTICAL_REACH; dy++) {
					for (int dz = -REACH; dz <= REACH; dz++) {
						p.setWithOffset(tee, dx, dy, dz);
						if (level.isLoaded(p) && level.getBlockState(p).getBlock() instanceof HoleSignBlock) write(level, p.immutable());
					}
				}
			}
		}
	}

	/** Tees of all holes of a course in this level, for {@link #refreshAround}. */
	public static List<BlockPos> tees(ServerLevel level, Course course) {
		return course.holes().stream().filter(h -> h.dimension().equals(level.dimension())).map(Hole::tee).toList();
	}

	static SignText textFor(ServerLevel level, BlockPos pos) {
		Course bestCourse = null;
		int bestIndex = -1;
		int bestDist = Integer.MAX_VALUE;
		for (Course course : CourseSavedData.get(level.getServer()).all()) {
			for (int i = 0; i < course.holes().size(); i++) {
				Hole h = course.holes().get(i);
				if (!h.dimension().equals(level.dimension())) continue;
				int dx = h.tee().getX() - pos.getX(), dy = h.tee().getY() - pos.getY(), dz = h.tee().getZ() - pos.getZ();
				if (Math.abs(dx) > REACH || Math.abs(dz) > REACH || Math.abs(dy) > VERTICAL_REACH) continue;
				int d = dx * dx + dy * dy + dz * dz;
				if (d < bestDist) {
					bestDist = d;
					bestCourse = course;
					bestIndex = i;
				}
			}
		}
		SignText.Mutable text = SignText.EMPTY.asMutable();
		if (bestCourse == null) return text.setLine(0, Component.translatable("minegolf.sign.no_hole")).asImmutable();
		Hole hole = bestCourse.holes().get(bestIndex);
		String name = bestCourse.name();
		if (name.length() > NAME_LENGTH) name = name.substring(0, NAME_LENGTH - 1) + "…";
		return text.setLine(0, Component.translatable("minegolf.sign.hole", bestIndex + 1))
				.setLine(1, Component.translatable("minegolf.sign.par", hole.par()))
				.setLine(2, Component.translatable("minegolf.sign.length", hole.length()))
				.setLine(3, Component.literal(name))
				.asImmutable();
	}
}
