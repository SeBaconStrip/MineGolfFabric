package net.colin.minegolf.gametest;

import com.mojang.serialization.DataResult;
import net.colin.minegolf.block.ModBlocks;
import net.colin.minegolf.course.Course;
import net.colin.minegolf.course.Hole;
import net.colin.minegolf.entity.GolfBallEntity;
import net.colin.minegolf.entity.ModEntities;
import net.colin.minegolf.item.ModItems;
import net.colin.minegolf.physics.Club;
import net.colin.minegolf.stats.GolfAdvancements;
import net.colin.minegolf.stats.GolfStats;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
		helper.assertTrue(count == 15, "expected 15 MineGolf recipes, found " + count);
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
}
