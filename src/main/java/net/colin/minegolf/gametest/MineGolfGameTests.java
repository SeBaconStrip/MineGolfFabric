package net.colin.minegolf.gametest;

import com.mojang.serialization.DataResult;
import net.colin.minegolf.block.ModBlocks;
import net.colin.minegolf.course.Course;
import net.colin.minegolf.course.CourseSavedData;
import net.colin.minegolf.course.Hole;
import net.colin.minegolf.course.HoleSigns;
import net.colin.minegolf.entity.GolfBallEntity;
import net.colin.minegolf.entity.GolfCartEntity;
import net.colin.minegolf.entity.ModEntities;
import net.colin.minegolf.item.GolfBagItem;
import net.colin.minegolf.item.ModItems;
import net.colin.minegolf.physics.Club;
import net.colin.minegolf.physics.ShotAdvisor;
import net.colin.minegolf.stats.GolfAdvancements;
import net.colin.minegolf.stats.GolfStats;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Runs with {@code ./gradlew runGametest}: real blocks and the real ball entity, no client. */
public class MineGolfGameTests {
	/** Lays a 3-wide lane along +z at relative y=0, the ball rolls on top of it at y=1. */
	private static void lane(GameTestHelper helper, Block block) {
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 8; z++) helper.setBlock(x, 0, z, block);
		}
	}

	private static GolfBallEntity ballAt(GameTestHelper helper, double z) {
		GolfBallEntity ball = new GolfBallEntity(ModEntities.GOLF_BALL, helper.getLevel());
		Vec3 pos = helper.absoluteVec(new Vec3(1.5, 1.0, z));
		ball.setPos(pos);
		helper.getLevel().addFreshEntity(ball);
		return ball;
	}

	private static double relativeZ(GameTestHelper helper, GolfBallEntity ball) {
		return ball.getZ() - helper.absolutePos(BlockPos.ZERO).getZ();
	}

	@GameTest(maxTicks = 200)
	public void puttRollsIntoCup(GameTestHelper helper) {
		lane(helper, ModBlocks.GREEN);
		helper.setBlock(1, 0, 6, ModBlocks.CUP);
		GolfBallEntity ball = ballAt(helper, 1.5);
		// About five blocks of roll on the green, slow enough to drop
		ball.hit(Club.PUTTER, 0.35, 0F);
		helper.succeedWhen(() -> helper.assertTrue(ball.isRemoved(), "ball should drop into the cup"));
	}

	@GameTest(maxTicks = 100)
	public void bunkerStopsBall(GameTestHelper helper) {
		lane(helper, ModBlocks.BUNKER_SAND);
		GolfBallEntity ball = ballAt(helper, 1.5);
		ball.hit(Club.PUTTER, 1.0, 0F);
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(ball.isResting(), "ball should rest in the bunker");
			helper.assertTrue(relativeZ(helper, ball) < 3.5, "ball rolled too far in sand: " + relativeZ(helper, ball));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void waterResetsBall(GameTestHelper helper) {
		lane(helper, ModBlocks.GREEN);
		for (int x = 0; x < 3; x++) {
			for (int z = 4; z < 8; z++) helper.setBlock(x, 1, z, Blocks.WATER);
		}
		GolfBallEntity ball = ballAt(helper, 1.5);
		ball.hit(Club.PUTTER, 1.0, 0F);
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(ball.isResting(), "ball should be resting again");
			helper.assertTrue(Math.abs(relativeZ(helper, ball) - 1.5) < 0.01, "ball should be back at its last rest spot");
			helper.succeed();
		});
	}

	@GameTest
	public void courseSurvivesSaving(GameTestHelper helper) {
		Hole hole = new Hole(Level.OVERWORLD, new BlockPos(1, 64, 2), new BlockPos(30, 64, 40), 4);
		Course course = new Course(UUID.randomUUID(), "Test Links", UUID.randomUUID(), List.of(hole));
		DataResult<Tag> saved = Course.CODEC.encodeStart(NbtOps.INSTANCE, course);
		Course loaded = Course.CODEC.parse(NbtOps.INSTANCE, saved.getOrThrow()).getOrThrow();
		helper.assertValueEqual(loaded.name(), "Test Links", "name");
		helper.assertValueEqual(loaded.holes(), List.of(hole), "holes");
		helper.succeed();
	}

	@GameTest
	public void courseHolesCanBeReorderedRemovedAndReplaced(GameTestHelper helper) {
		Hole a = new Hole(Level.OVERWORLD, new BlockPos(0, 64, 0), new BlockPos(20, 64, 0), 2);
		Hole b = new Hole(Level.OVERWORLD, new BlockPos(0, 64, 10), new BlockPos(30, 64, 10), 3);
		Hole c = new Hole(Level.OVERWORLD, new BlockPos(0, 64, 20), new BlockPos(40, 64, 20), 4);
		Course course = new Course(UUID.randomUUID(), "Edit", UUID.randomUUID(), List.of(a, b, c));

		course.moveHole(2, -1);
		helper.assertValueEqual(course.holes(), List.of(a, c, b), "after moving hole 3 up");
		course.moveHole(0, -1);
		course.moveHole(2, 1);
		helper.assertValueEqual(course.holes(), List.of(a, c, b), "moves past the ends do nothing");

		course.removeHole(1);
		helper.assertValueEqual(course.holes(), List.of(a, b), "after removing the middle hole");

		Hole relinked = new Hole(Level.OVERWORLD, new BlockPos(5, 64, 5), new BlockPos(50, 64, 5), a.par());
		course.setHole(0, relinked);
		helper.assertValueEqual(course.holes(), List.of(relinked, b), "after relinking hole 1");
		helper.assertTrue(course.uses(new BlockPos(5, 64, 5), -1), "tee belongs to the course");
		helper.assertTrue(!course.uses(new BlockPos(5, 64, 5), 0), "a hole being relinked may reuse its own blocks");
		helper.succeed();
	}

	@GameTest
	public void everyRecipeUnlocksInRecipeBook(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Set<ResourceKey<Recipe<?>>> rewarded = new HashSet<>();
		for (AdvancementHolder advancement : server.getAdvancements().getAllAdvancements()) {
			rewarded.addAll(advancement.value().rewards().recipes());
		}
		int count = 0;
		for (RecipeHolder<?> recipe : server.getRecipeManager().getRecipes()) {
			if (!recipe.id().identifier().getNamespace().equals("minegolf")) continue;
			count++;
			helper.assertTrue(rewarded.contains(recipe.id()), "no recipe book unlock for " + recipe.id().identifier());
		}
		helper.assertTrue(count == 19, "expected 19 MineGolf recipes, found " + count);
		helper.succeed();
	}

	private static boolean hasAdvancement(ServerPlayer player, String name) {
		AdvancementHolder holder = player.level().getServer().getAdvancements()
				.get(Identifier.fromNamespaceAndPath("minegolf", "golf/" + name));
		return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
	}

	/** A practice ball owned by a fake player, resting on the lane. */
	private static GolfBallEntity ownedBall(GameTestHelper helper, ServerPlayer player) {
		Vec3 pos = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
		return GolfBallEntity.place(helper.getLevel(), player, new ItemStack(ModItems.GOLF_BALL), pos.x, pos.y, pos.z,
				GolfBallEntity.PRACTICE);
	}

	@GameTest
	public void everyAdvancementInCodeExists(GameTestHelper helper) throws IllegalAccessException {
		MinecraftServer server = helper.getLevel().getServer();
		int count = 0;
		for (Field field : GolfAdvancements.class.getFields()) {
			if (field.getType() != String.class || !Modifier.isStatic(field.getModifiers())) continue;
			String name = (String) field.get(null);
			helper.assertTrue(server.getAdvancements().get(Identifier.fromNamespaceAndPath("minegolf", "golf/" + name)) != null,
					"missing advancement minegolf:golf/" + name);
			count++;
		}
		helper.assertTrue(count == 19, "expected 19 advancements in code, found " + count);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void waterCountsAndAwardsSplash(GameTestHelper helper) {
		lane(helper, ModBlocks.GREEN);
		for (int x = 0; x < 3; x++) {
			for (int z = 4; z < 8; z++) helper.setBlock(x, 1, z, Blocks.WATER);
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		GolfBallEntity ball = ownedBall(helper, player);
		ball.hit(Club.PUTTER, 1.0, 0F);
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(player.getStats().getValue(Stats.CUSTOM.get(GolfStats.BALLS_IN_WATER)) == 1, "one ball in water");
			helper.assertTrue(hasAdvancement(player, GolfAdvancements.SPLASH), "Splash! should be awarded");
			helper.assertTrue(!hasAdvancement(player, GolfAdvancements.HOT_SHOT), "Hot Shot is for lava only");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void bunkerAwardsBeachDay(GameTestHelper helper) {
		lane(helper, ModBlocks.BUNKER_SAND);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		GolfBallEntity ball = ownedBall(helper, player);
		ball.hit(Club.PUTTER, 1.0, 0F);
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(ball.isResting(), "ball should rest in the bunker");
			helper.assertTrue(hasAdvancement(player, GolfAdvancements.BEACH_DAY), "Beach Day should be awarded");
			helper.succeed();
		});
	}

	@GameTest
	public void turfCountsAsGroundForHeightmaps(GameTestHelper helper) {
		for (Block block : List.of(ModBlocks.FAIRWAY, ModBlocks.ROUGH, ModBlocks.GREEN, ModBlocks.BUNKER_SAND, ModBlocks.CUP)) {
			helper.assertTrue(block.defaultBlockState().is(net.minecraft.tags.BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP_NO_LEAVES),
					block + " should count as ground");
		}
		helper.succeed();
	}

	@GameTest
	public void playerCanLeaveRound(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, ModBlocks.TEE_BOX);
		helper.setBlock(6, 0, 6, ModBlocks.CUP);
		Hole hole = new Hole(Level.OVERWORLD, helper.absolutePos(new BlockPos(1, 1, 1)), helper.absolutePos(new BlockPos(6, 0, 6)), 3);
		Course course = new Course(UUID.randomUUID(), "Leave Test", UUID.randomUUID(), List.of(hole));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		net.colin.minegolf.round.RoundManager rounds = net.colin.minegolf.round.RoundManager.get(helper.getLevel().getServer());
		rounds.start(player, course);
		helper.assertTrue(rounds.roundOf(player) != null, "player should be in the round");
		rounds.quit(player);
		helper.assertTrue(rounds.roundOf(player) == null, "player should have left the round");
		helper.succeed();
	}

	@GameTest
	public void holeSignShowsHoleAndFollowsParChange(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, ModBlocks.TEE_BOX);
		helper.setBlock(6, 0, 6, ModBlocks.CUP);
		BlockPos tee = helper.absolutePos(new BlockPos(1, 1, 1));
		Course course = CourseSavedData.get(helper.getLevel().getServer()).create("Sign Test Links", UUID.randomUUID());
		course.addHole(new Hole(helper.getLevel().dimension(), tee, helper.absolutePos(new BlockPos(6, 0, 6)), 3));
		helper.setBlock(3, 1, 1, ModBlocks.HOLE_SIGN);
		BlockPos signPos = helper.absolutePos(new BlockPos(3, 1, 1));
		HoleSigns.write(helper.getLevel(), signPos);

		SignBlockEntity sign = (SignBlockEntity) helper.getLevel().getBlockEntity(signPos);
		helper.assertTrue(sign != null && sign.isWaxed(), "hole sign should be locked");
		int holeNumber = course.holes().size();
		helper.assertValueEqual(sign.getText(SignTextSlot.FRONT).getMessages(false).get(0),
				Component.translatable("minegolf.sign.hole", holeNumber), "hole line");
		helper.assertValueEqual(sign.getText(SignTextSlot.FRONT).getMessages(false).get(1),
				Component.translatable("minegolf.sign.par", 3), "par line");
		helper.assertValueEqual(sign.getText(SignTextSlot.BACK).getMessages(false).get(3), Component.literal("Sign Test Links"), "back side");

		course.changePar(0, 1);
		HoleSigns.refreshAround(helper.getLevel(), HoleSigns.tees(helper.getLevel(), course));
		helper.assertValueEqual(sign.getText(SignTextSlot.FRONT).getMessages(false).get(1),
				Component.translatable("minegolf.sign.par", 4), "par line after change");
		helper.succeed();
	}

	@GameTest(maxTicks = 400)
	public void ballBehindStakeLineIsOutOfBounds(GameTestHelper helper) {
		lane(helper, ModBlocks.GREEN);
		// A short boundary across the lane; the ball rolls through the middle stake
		for (int x = 0; x < 3; x++) helper.setBlock(x, 1, 4, ModBlocks.OOB_STAKE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		GolfBallEntity ball = ownedBall(helper, player);
		ball.hit(Club.PUTTER, ShotAdvisor.suggest(5, 0, false, true).power(), 0F);
		// A slow putt on the green takes a while to settle
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(ball.isResting(), "ball should rest again, at z=" + relativeZ(helper, ball) + " y=" + (ball.getY() - helper.absolutePos(BlockPos.ZERO).getY()) + " removed=" + ball.isRemoved());
			helper.assertTrue(Math.abs(relativeZ(helper, ball) - 1.5) < 0.01,
					"ball should be back where it was hit, was at z=" + relativeZ(helper, ball));
			helper.succeed();
		});
	}

	private static GolfCartEntity cartAt(GameTestHelper helper, double x, double z) {
		GolfCartEntity cart = new GolfCartEntity(ModEntities.GOLF_CART, helper.getLevel());
		cart.setPos(helper.absoluteVec(new Vec3(x, 1, z)));
		helper.getLevel().addFreshEntity(cart);
		return cart;
	}

	private static ItemStack bagWith(ItemStack... items) {
		ItemStack bag = new ItemStack(ModItems.GOLF_BAG);
		bag.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(items)));
		return bag;
	}

	@GameTest
	public void cartItemPlacesCart(GameTestHelper helper) {
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) helper.setBlock(x, 0, z, ModBlocks.FAIRWAY);
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.GOLF_CART));
		BlockPos ground = helper.absolutePos(new BlockPos(2, 0, 2));
		Vec3 top = Vec3.atBottomCenterOf(ground.above());
		ModItems.GOLF_CART.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(top, Direction.UP, ground, false)));
		List<GolfCartEntity> carts = helper.getLevel().getEntitiesOfClass(GolfCartEntity.class, new AABB(ground).inflate(2));
		helper.assertTrue(carts.size() == 1, "expected one cart, found " + carts.size());
		helper.succeed();
	}

	@GameTest
	public void cartHoldsBagAndDropsItWhenBroken(GameTestHelper helper) {
		GolfCartEntity cart = cartAt(helper, 2.5, 2.5);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemInHand(InteractionHand.MAIN_HAND, bagWith(new ItemStack(ModItems.PUTTER)));
		cart.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
		helper.assertTrue(cart.hasBag(), "bag should be on the cart");
		helper.assertTrue(player.getMainHandItem().isEmpty(), "bag should have left the hand");

		cart.destroy(helper.getLevel(), ModItems.GOLF_CART);
		List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, cart.getBoundingBox().inflate(2));
		helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(ModItems.GOLF_CART)), "cart item should drop");
		helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(ModItems.GOLF_BAG)), "bag should drop with the cart");
		helper.succeed();
	}

	@GameTest
	public void clubKeyUsesCartBag(GameTestHelper helper) {
		GolfCartEntity cart = cartAt(helper, 2.5, 2.5);
		cart.setBag(bagWith(ItemStack.EMPTY, new ItemStack(ModItems.IRON)));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(player.startRiding(cart), "player should get into the cart");
		GolfBagItem.cycleClub(player);
		helper.assertTrue(player.getMainHandItem().is(ModItems.IRON), "iron should come from the cart's bag");
		helper.succeed();
	}
}
