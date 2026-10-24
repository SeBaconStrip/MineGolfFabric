package net.colin.minegolf.course;

import com.mojang.serialization.Codec;
import net.colin.minegolf.MineGolf;
import net.colin.minegolf.block.ModBlocks;
import net.colin.minegolf.item.CourseBookItem;
import net.colin.minegolf.physics.OutOfBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Where the out-of-bounds stakes of one dimension stand. Stakes found missing are dropped when read. */
public final class StakeIndex extends SavedData {
	private static final Codec<StakeIndex> CODEC = BlockPos.CODEC.listOf()
			.xmap(StakeIndex::new, index -> List.copyOf(index.stakes));

	public static final SavedDataType<StakeIndex> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(MineGolf.MOD_ID, "oob_stakes"), StakeIndex::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	public static final double SHOW_RADIUS = 48;
	private static final DustParticleOptions LINE_DUST = new DustParticleOptions(0xFFFFFF, 1.0F);

	private final Set<BlockPos> stakes = new HashSet<>();

	public StakeIndex() {
	}

	private StakeIndex(List<BlockPos> list) {
		stakes.addAll(list);
	}

	public static StakeIndex get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	public void add(BlockPos pos) {
		if (stakes.add(pos.immutable())) setDirty();
	}

	/** Stakes within radius of (x, z), horizontally. */
	public List<BlockPos> near(ServerLevel level, double x, double z, double radius) {
		List<BlockPos> found = new ArrayList<>();
		for (Iterator<BlockPos> it = stakes.iterator(); it.hasNext(); ) {
			BlockPos p = it.next();
			double dx = p.getX() + 0.5 - x, dz = p.getZ() + 0.5 - z;
			if (dx * dx + dz * dz > radius * radius) continue;
			if (level.isLoaded(p) && !level.getBlockState(p).is(ModBlocks.OOB_STAKE)) {
				it.remove();
				setDirty();
				continue;
			}
			found.add(p);
		}
		return found;
	}

	/**
	 * Boundary lines that can reach within radius of (x, z). Stakes one more gap further out are included so the
	 * stakes that matter see all their neighbours and link the same way as from anywhere else.
	 */
	public List<OutOfBounds.Line> linesNear(ServerLevel level, double x, double z, double radius) {
		List<double[]> points = new ArrayList<>();
		for (BlockPos p : near(level, x, z, radius + 2 * OutOfBounds.MAX_GAP)) {
			points.add(new double[] {p.getX() + 0.5, p.getZ() + 0.5, p.getY()});
		}
		return OutOfBounds.lines(points);
	}

	/** Whether a shot from (x0, z0) that stopped at (x1, z1) ended out of bounds. */
	public static boolean isOut(ServerLevel level, double x0, double z0, double x1, double z1) {
		double length = Math.sqrt((x1 - x0) * (x1 - x0) + (z1 - z0) * (z1 - z0));
		List<OutOfBounds.Line> lines = get(level).linesNear(level, (x0 + x1) / 2, (z0 + z1) / 2, length / 2 + 1);
		return OutOfBounds.isOut(x0, z0, x1, z1, lines);
	}

	/** Dots the lines near a player who holds a stake or a Course Book. */
	public static void showLines(ServerPlayer player) {
		if (!holdsStakeOrBook(player) || !(player.level() instanceof ServerLevel level)) return;
		for (OutOfBounds.Line l : get(level).linesNear(level, player.getX(), player.getZ(), SHOW_RADIUS)) {
			double dx = l.x1() - l.x0(), dz = l.z1() - l.z0();
			int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dz * dz)));
			for (int i = 0; i <= steps; i++) {
				double t = i / (double) steps;
				double y = l.y0() + (l.y1() - l.y0()) * t + 0.5;
				level.sendParticles(player, LINE_DUST, true, false, l.x0() + dx * t, y, l.z0() + dz * t, 1, 0, 0, 0, 0);
			}
		}
	}

	private static boolean holdsStakeOrBook(ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.is(ModBlocks.OOB_STAKE.asItem()) || stack.getItem() instanceof CourseBookItem) return true;
		}
		return false;
	}
}
