package net.colin.minegolf.block;

import net.colin.minegolf.course.HoleSigns;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import org.jspecify.annotations.Nullable;

/** A birch sign that writes the number, par and length of the hole whose tee box stands next to it. */
public class HoleSignBlock extends StandingSignBlock {
	public HoleSignBlock(BlockBehaviour.Properties properties) {
		super(WoodType.BIRCH, properties);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
		// No text editor: the sign fills itself
		if (level instanceof ServerLevel serverLevel) HoleSigns.write(serverLevel, pos);
	}
}
