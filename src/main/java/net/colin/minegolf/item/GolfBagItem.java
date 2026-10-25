package net.colin.minegolf.item;

import net.colin.minegolf.physics.Club;
import net.colin.minegolf.entity.GolfCartEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

import java.util.List;

/** Carries four clubs and a stack of balls; lets the player cycle the club in hand with a key. */
public class GolfBagItem extends Item {
	public GolfBagItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			ItemStack bag = player.getItemInHand(hand);
			int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND;
			serverPlayer.openMenu(new SimpleMenuProvider(
					(id, inventory, p) -> new GolfBagMenu(id, inventory, bag, slot), bag.getHoverName()));
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * Swaps the club in the main hand with the next club in the first bag found in the inventory, or in the bag on
	 * the cart the player rides.
	 * An empty hand takes the first club in the bag.
	 */
	public static void cycleClub(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		ItemStack bag = ItemStack.EMPTY;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).getItem() instanceof GolfBagItem) {
				bag = inventory.getItem(i);
				break;
			}
		}
		// Riding a cart that carries a bag works like having the bag along
		if (bag.isEmpty() && player.getVehicle() instanceof GolfCartEntity cart) bag = cart.bag();
		if (bag.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("minegolf.msg.no_bag"));
			return;
		}
		ItemStack held = player.getMainHandItem();
		if (!held.isEmpty() && !(held.getItem() instanceof ClubItem)) {
			player.sendOverlayMessage(Component.translatable("minegolf.msg.hand_not_free"));
			return;
		}

		List<ItemStack> items = GolfBagMenu.readItems(bag.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY));
		int start = held.getItem() instanceof ClubItem club ? club.club().ordinal() + 1 : 0;
		int pick = -1;
		Club[] order = Club.values();
		for (int step = 0; step < order.length && pick < 0; step++) {
			Club wanted = order[(start + step) % order.length];
			for (int slot = 0; slot < GolfBagMenu.BALL_SLOT; slot++) {
				if (items.get(slot).getItem() instanceof ClubItem c && c.club() == wanted) {
					pick = slot;
					break;
				}
			}
		}
		if (pick < 0) {
			player.sendOverlayMessage(Component.translatable("minegolf.msg.bag_no_clubs"));
			return;
		}

		ItemStack next = items.get(pick);
		items.set(pick, held.copy());
		bag.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
		player.setItemInHand(InteractionHand.MAIN_HAND, next.copy());
		player.sendOverlayMessage(next.getHoverName());
	}
}
