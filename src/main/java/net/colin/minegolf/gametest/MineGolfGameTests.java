package net.colin.minegolf.gametest;

import com.mojang.serialization.DataResult;
import net.colin.minegolf.block.ModBlocks;
import net.colin.minegolf.course.Course;
import net.colin.minegolf.course.Hole;
import net.colin.minegolf.entity.GolfBallEntity;
import net.colin.minegolf.entity.ModEntities;
import net.colin.minegolf.physics.Club;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
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
}
