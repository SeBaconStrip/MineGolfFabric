package net.colin.minegolf.item;

import net.colin.minegolf.MineGolf;
import net.colin.minegolf.block.ModBlocks;
import net.colin.minegolf.physics.Club;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.function.Function;

public class ModItems {
	public static final Item GOLF_BALL = register("golf_ball", GolfBallItem::new, new Item.Properties().stacksTo(16));
	public static final Item DRIVER = club("driver", Club.DRIVER);
	public static final Item IRON = club("iron", Club.IRON);
	public static final Item WEDGE = club("wedge", Club.WEDGE);
	public static final Item PUTTER = club("putter", Club.PUTTER);
	public static final Item GOLF_BAG = register("golf_bag", GolfBagItem::new,
			new Item.Properties().stacksTo(1).component(DataComponents.CONTAINER, ItemContainerContents.EMPTY));
	public static final Item COURSE_BOOK = register("course_book", CourseBookItem::new, new Item.Properties().stacksTo(1));
	public static final Item RANGE_FINDER = register("range_finder", Item::new, new Item.Properties().stacksTo(1));

	public static final ResourceKey<CreativeModeTab> TAB_KEY =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(MineGolf.MOD_ID, "golf"));

	private static Item club(String name, Club club) {
		return register(name, p -> new ClubItem(club, p), new Item.Properties().stacksTo(1));
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineGolf.MOD_ID, name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void register() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.minegolf.golf"))
				.icon(() -> new ItemStack(DRIVER))
				.displayItems((parameters, output) -> {
					output.accept(DRIVER);
					output.accept(IRON);
					output.accept(WEDGE);
					output.accept(PUTTER);
					output.accept(GOLF_BALL);
					output.accept(GOLF_BAG);
					output.accept(COURSE_BOOK);
					output.accept(RANGE_FINDER);
					output.accept(ModBlocks.TEE_BOX);
					output.accept(ModBlocks.CUP);
					output.accept(ModBlocks.FLAG);
					output.accept(ModBlocks.HOLE_SIGN);
					output.accept(ModBlocks.OOB_STAKE);
					output.accept(ModBlocks.FAIRWAY);
					output.accept(ModBlocks.ROUGH);
					output.accept(ModBlocks.GREEN);
					output.accept(ModBlocks.BUNKER_SAND);
				})
				.build());
	}
}
