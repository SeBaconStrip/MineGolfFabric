package net.colin.minegolf.client;

import net.colin.minegolf.entity.GolfBallEntity;
import net.colin.minegolf.entity.LevelSurfaceLookup;
import net.colin.minegolf.item.ClubItem;
import net.colin.minegolf.physics.BallPhysics;
import net.colin.minegolf.physics.BallState;
import net.colin.minegolf.physics.Club;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.item.ItemStack;

/** While a swing charges, dots the first 1.5 s of the predicted flight with the same physics as the server. */
public final class AimLine {
	private static final int PREVIEW_TICKS = 30;
	private static final DustParticleOptions DOT = new DustParticleOptions(0xFFFFFF, 0.6F);

	private AimLine() {
	}

	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || !player.isUsingItem()) return;
		ItemStack using = player.getUseItem();
		if (!(using.getItem() instanceof ClubItem club)) return;
		int ticks = ClubItem.chargeTicks(player, using);
		double power = Club.powerForTicks(ticks);
		if (power <= 0 || ticks % 2 != 0) return;
		GolfBallEntity ball = GolfBallEntity.findHittable(player.level(), player);
		if (ball == null) return;

		LevelSurfaceLookup lookup = new LevelSurfaceLookup(player.level());
		BallState s = ball.stateCopy();
		s.onGround = true;
		BallPhysics.launch(s, club.club(), power, player.getYRot(), BallPhysics.surfaceUnder(s, lookup));
		for (int i = 0; i < PREVIEW_TICKS; i++) {
			BallPhysics.Event event = BallPhysics.step(s, lookup);
			if (i % 2 == 1) player.level().addParticle(DOT, s.x, s.y + 0.1, s.z, 0, 0, 0);
			if (event != BallPhysics.Event.NONE) break;
		}
	}
}
