package net.colin.minegolf.item;

import net.colin.minegolf.entity.GolfCartEntity;
import net.colin.minegolf.entity.ModEntities;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/** Sets a golf cart down on the clicked block top, facing where the player looks. */
public class GolfCartItem extends Item {
	public GolfCartItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getClickedFace() != Direction.UP) return InteractionResult.PASS;
		if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
		Vec3 at = context.getClickLocation();
		GolfCartEntity cart = new GolfCartEntity(ModEntities.GOLF_CART, level);
		cart.setPos(at.x, at.y, at.z);
		Player player = context.getPlayer();
		if (player != null) cart.setYRot(player.getYRot());
		if (!level.noCollision(cart, cart.getBoundingBox())) return InteractionResult.FAIL;
		level.addFreshEntity(cart);
		cart.playPlaceSound();
		level.gameEvent(player, GameEvent.ENTITY_PLACE, at);
		context.getItemInHand().consume(1, player);
		return InteractionResult.SUCCESS;
	}
}
