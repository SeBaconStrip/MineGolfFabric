package net.colin.minegolf.entity;

import net.colin.minegolf.MineGolf;
import net.colin.minegolf.block.ModBlocks;
import net.colin.minegolf.physics.Surface;
import net.colin.minegolf.physics.SurfaceLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Reads the real world for the ball physics. Works on both the server and the client level. */
public final class LevelSurfaceLookup implements SurfaceLookup {
	public static final TagKey<Block> GREEN = tag("green");
	public static final TagKey<Block> FAIRWAY = tag("fairway");
	public static final TagKey<Block> BUNKER = tag("bunker");
	public static final TagKey<Block> ICE = tag("ice");

	private final Level level;
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

	public LevelSurfaceLookup(Level level) {
		this.level = level;
	}

	private static TagKey<Block> tag(String surface) {
		return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MineGolf.MOD_ID, "surface/" + surface));
	}

	@Override
	public double height(int x, int y, int z) {
		BlockState state = level.getBlockState(pos.set(x, y, z));
		if (state.isAir()) return 0;
		VoxelShape shape = state.getCollisionShape(level, pos);
		return shape.isEmpty() ? 0 : Math.min(1.0, shape.max(Direction.Axis.Y));
	}

	@Override
	public Surface surface(int x, int y, int z) {
		BlockState state = level.getBlockState(pos.set(x, y, z));
		if (state.is(GREEN)) return Surface.GREEN;
		if (state.is(FAIRWAY)) return Surface.FAIRWAY;
		if (state.is(BUNKER)) return Surface.BUNKER;
		if (state.is(ICE)) return Surface.ICE;
		return Surface.ROUGH;
	}

	@Override
	public boolean hazard(int x, int y, int z) {
		FluidState fluid = level.getFluidState(pos.set(x, y, z));
		return fluid.is(FluidTags.WATER) || fluid.is(FluidTags.LAVA);
	}

	@Override
	public boolean isCup(int x, int y, int z) {
		return level.getBlockState(pos.set(x, y, z)).is(ModBlocks.CUP);
	}

	@Override
	public int minY() {
		return level.getMinY();
	}
}
