package net.colin.minegolf.block;

import net.colin.minegolf.MineGolf;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.function.Function;

public class ModBlocks {
	public static final Block TEE_BOX = register("tee_box", TeeBoxBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).strength(0.1F).sound(SoundType.WOOL));
	public static final Block CUP = register("cup", Block::new, turf());
	public static final Block FLAG = register("flag", FlagBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).noCollision().instabreak()
					.sound(SoundType.WOOD).pushReaction(PushReaction.POPPED));

	public static final Block OOB_STAKE = register("oob_stake", OutOfBoundsStakeBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).noCollision().instabreak()
					.sound(SoundType.WOOD).pushReaction(PushReaction.POPPED));

	public static final Block HOLE_SIGN = register("hole_sign", HoleSignBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.SAND).forceSolidOn().instrument(NoteBlockInstrument.BASS)
					.noCollision().strength(1.0F).ignitedByLava());

	public static final Block FAIRWAY = register("fairway", Block::new, turf());
	public static final Block ROUGH = register("rough", Block::new, turf());
	public static final Block GREEN = register("green", Block::new, turf());
	public static final Block BUNKER_SAND = register("bunker_sand",
			p -> new ColoredFallingBlock(new ColorRGBA(0xE8DCA8), p),
			BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.SNARE)
					.strength(0.5F).sound(SoundType.SAND));

	private static BlockBehaviour.Properties turf() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).strength(0.6F).sound(SoundType.GRASS);
	}

	/** Registers the block and a matching block item. */
	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory,
								  BlockBehaviour.Properties properties) {
		Identifier id = Identifier.fromNamespaceAndPath(MineGolf.MOD_ID, name);
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		Registry.register(BuiltInRegistries.ITEM, itemKey,
				new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	public static void register() {
		MineGolf.LOGGER.debug("Registering blocks");
		// The vanilla sign block entity and renderer draw the hole sign
		BlockEntityTypes.SIGN.addValidBlock(HOLE_SIGN);
	}
}
