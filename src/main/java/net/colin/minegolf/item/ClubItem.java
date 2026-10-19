package net.colin.minegolf.item;

import net.colin.minegolf.physics.Club;
import net.minecraft.world.item.Item;

public class ClubItem extends Item {
	private final Club club;

	public ClubItem(Club club, Item.Properties properties) {
		super(properties);
		this.club = club;
	}

	public Club club() {
		return club;
	}
}
