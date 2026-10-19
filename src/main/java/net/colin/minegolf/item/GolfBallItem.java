package net.colin.minegolf.item;

import net.colin.minegolf.entity.GolfBallEntity;
import net.colin.minegolf.entity.LevelSurfaceLookup;
import net.colin.minegolf.round.RoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/** Placed on the top of a block: on the hole's tee during a round, anywhere for practice. */
public class GolfBallItem extends Item {
	/** Old practice balls this far away are collected when a new one is placed. */
	private static final double PRACTICE_RECALL_RANGE = 96;

	public GolfBallItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getClickedFace() != Direction.UP || context.getPlayer() == null) return InteractionResult.PASS;
		if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = context.getItemInHand();
		BlockPos pos = context.getClickedPos();
		RoundManager rounds = RoundManager.get(level.getServer());

		if (rounds.roundOf(player) != null) {
			return rounds.teeUp(player, level, pos, stack) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
		}

		// Practice: one ball per player, the previous one comes back
		for (GolfBallEntity old : GolfBallEntity.findOwned(level, player, PRACTICE_RECALL_RANGE)) {
			if (old.roundId() == GolfBallEntity.PRACTICE) {
				old.returnToOwner();
				old.discard();
			}
		}
		double top = new LevelSurfaceLookup(level).height(pos.getX(), pos.getY(), pos.getZ());
		GolfBallEntity.place(level, player, stack, context.getClickLocation().x, pos.getY() + top,
				context.getClickLocation().z, GolfBallEntity.PRACTICE);
		stack.consume(1, player);
		player.sendOverlayMessage(Component.translatable("minegolf.msg.practice_ball"));
		return InteractionResult.SUCCESS;
	}
}
