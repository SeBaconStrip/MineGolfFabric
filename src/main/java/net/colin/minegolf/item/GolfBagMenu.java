package net.colin.minegolf.item;

import net.minecraft.core.NonNullList;
import net.colin.minegolf.physics.Club;
import net.colin.minegolf.stats.GolfAdvancements;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Server side of the golf bag screen. Uses the hopper menu type and slot layout, so clients show the
 * vanilla hopper screen; the slot rules (clubs left, balls right) are enforced here.
 */
public class GolfBagMenu extends AbstractContainerMenu {
	public static final int SLOTS = 5;
	public static final int BALL_SLOT = 4;

	private final Container bag;
	private final ItemStack bagStack;
	private final int bagInventorySlot;

	public GolfBagMenu(int containerId, Inventory inventory, ItemStack bagStack, int bagInventorySlot) {
		super(MenuType.HOPPER, containerId);
		this.bagStack = bagStack;
		this.bagInventorySlot = bagInventorySlot;
		this.bag = new SimpleContainer(SLOTS) {
			@Override
			public void setChanged() {
				super.setChanged();
				save();
			}
		};
		load();

		for (int i = 0; i < SLOTS; i++) {
			int index = i;
			addSlot(new Slot(bag, i, 44 + i * 18, 20) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return index == BALL_SLOT ? stack.getItem() instanceof GolfBallItem : stack.getItem() instanceof ClubItem;
				}
			});
		}
		// Player inventory in the hopper layout; the bag's own slot is locked so it cannot be moved away
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addInventorySlot(inventory, col + row * 9 + 9, 8 + col * 18, 51 + row * 18);
			}
		}
		for (int col = 0; col < 9; col++) {
			addInventorySlot(inventory, col, 8 + col * 18, 109);
		}
	}

	private void addInventorySlot(Inventory inventory, int index, int x, int y) {
		addSlot(new Slot(inventory, index, x, y) {
			@Override
			public boolean mayPickup(Player player) {
				return index != bagInventorySlot;
			}
		});
	}

	private void load() {
		ItemContainerContents contents = bagStack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		List<ItemStack> items = readItems(contents);
		for (int i = 0; i < SLOTS && i < items.size(); i++) bag.setItem(i, items.get(i));
	}

	private void save() {
		List<ItemStack> items = new ArrayList<>(SLOTS);
		for (int i = 0; i < SLOTS; i++) items.add(bag.getItem(i));
		bagStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
	}

	/** Slot-ordered copy of a bag's contents. */
	public static List<ItemStack> readItems(ItemContainerContents contents) {
		NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
		contents.copyInto(list);
		return list;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (!(player instanceof ServerPlayer serverPlayer)) return;
		EnumSet<Club> clubs = EnumSet.noneOf(Club.class);
		for (int i = 0; i < BALL_SLOT; i++) {
			if (bag.getItem(i).getItem() instanceof ClubItem club) clubs.add(club.club());
		}
		if (clubs.size() == Club.values().length) GolfAdvancements.award(serverPlayer, GolfAdvancements.FULLY_EQUIPPED);
	}

	@Override
	public boolean stillValid(Player player) {
		return player.getInventory().getItem(bagInventorySlot) == bagStack;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		ItemStack clicked = ItemStack.EMPTY;
		Slot slot = slots.get(slotIndex);
		if (slot.hasItem() && slot.mayPickup(player)) {
			ItemStack stack = slot.getItem();
			clicked = stack.copy();
			if (slotIndex < SLOTS) {
				if (!moveItemStackTo(stack, SLOTS, slots.size(), true)) return ItemStack.EMPTY;
			} else if (!moveItemStackTo(stack, 0, SLOTS, false)) {
				return ItemStack.EMPTY;
			}
			if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
			else slot.setChanged();
		}
		return clicked;
	}
}
