package net.colin.minegolf.item;

import net.colin.minegolf.entity.GolfBallEntity;
import net.colin.minegolf.physics.Club;
import net.colin.minegolf.round.RoundManager;
import net.colin.minegolf.stats.GolfAdvancements;
import net.colin.minegolf.stats.GolfStats;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * Hold use near your resting ball to charge, release to hit. The release runs on the server
 * ({@link #releaseUsing}), so the swing needs no packet of its own.
 */
public class ClubItem extends Item {
	private static final int USE_DURATION = 72000;

	private final Club club;

	public ClubItem(Club club, Item.Properties properties) {
		super(properties);
		this.club = club;
	}

	public Club club() {
		return club;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		GolfBallEntity ball = GolfBallEntity.findHittable(level, player);
		Component problem = null;
		if (ball == null) {
			problem = Component.translatable("minegolf.msg.no_ball");
		} else if (club == Club.DRIVER && !ball.isOnTee()) {
			problem = Component.translatable("minegolf.msg.driver_tee_only");
		} else if (player instanceof ServerPlayer serverPlayer
				&& !RoundManager.get(serverPlayer.level().getServer()).canHit(serverPlayer, ball)) {
			problem = Component.translatable("minegolf.msg.not_your_turn");
		}
		if (problem != null) {
			if (!level.isClientSide()) player.sendOverlayMessage(problem);
			return InteractionResult.FAIL;
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return USE_DURATION;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.NONE;
	}

	/** Ticks the use button has been held so far. */
	public static int chargeTicks(LivingEntity user, ItemStack stack) {
		return USE_DURATION - user.getUseItemRemainingTicks();
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
		if (!(entity instanceof ServerPlayer player)) return false;
		double power = Club.powerForTicks(USE_DURATION - remainingTime);
		GolfBallEntity ball = GolfBallEntity.findHittable(level, player);
		if (ball == null) return false;
		if (power <= 0) {
			player.sendOverlayMessage(Component.translatable("minegolf.msg.overcharged"));
			player.awardStat(GolfStats.WASTED_SWINGS);
			if (GolfStats.get(player, GolfStats.WASTED_SWINGS) >= GolfAdvancements.OVERTHINKER_SWINGS) {
				GolfAdvancements.award(player, GolfAdvancements.OVERTHINKER);
			}
			return false;
		}
		RoundManager rounds = RoundManager.get(player.level().getServer());
		if (!rounds.canHit(player, ball) || (club == Club.DRIVER && !ball.isOnTee())) return false;
		ball.hit(club, power, player.getYRot());
		player.awardStat(GolfStats.STROKES);
		GolfAdvancements.award(player, GolfAdvancements.FORE);
		rounds.onStroke(player, ball);
		return true;
	}
}
