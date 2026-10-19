package net.colin.minegolf.block;

import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Thin mat that marks where a hole starts. The ball is teed up by using a golf ball on it. */
public class TeeBoxBlock extends CarpetBlock {
	public TeeBoxBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}
}
