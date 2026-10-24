package net.colin.minegolf.client;

import net.colin.minegolf.block.ModBlocks;
import net.colin.minegolf.entity.GolfBallEntity;
import net.colin.minegolf.entity.LevelSurfaceLookup;
import net.colin.minegolf.item.ModItems;
import net.colin.minegolf.physics.ShotAdvisor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** While a Range Finder is held: distance, height and a club for the block in view, measured once per tick. */
public final class RangeFinderHud {
	public static final double RANGE = 256;
	private static final int WHITE = 0xFFFFFFFF;

	private static @Nullable Component line;

	private RangeFinderHud() {
	}

	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		line = player != null && holds(player) ? measure(player) : null;
	}

	public static void extract(GuiGraphicsExtractor g, Font font) {
		if (line == null) return;
		int y = g.guiHeight() - 72;
		int w = font.width(line);
		g.fill(g.guiWidth() / 2 - w / 2 - 3, y - 2, g.guiWidth() / 2 + w / 2 + 3, y + 10, 0x90000000);
		g.centeredText(font, line, g.guiWidth() / 2, y, WHITE);
	}

	private static boolean holds(LocalPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			if (player.getItemInHand(hand).is(ModItems.RANGE_FINDER)) return true;
		}
		return false;
	}

	private static @Nullable Component measure(LocalPlayer player) {
		Level level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1F).scale(RANGE));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.MISS) return null;

		BlockPos target = hit.getBlockPos();
		BlockPos cup = cupAt(level, target);
		if (cup != null) target = cup;
		BlockState state = level.getBlockState(target);
		double dx = target.getX() + 0.5 - player.getX();
		double dz = target.getZ() + 0.5 - player.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		double rise = target.getY() + 1 - player.getY();

		GolfBallEntity ball = GolfBallEntity.findHittable(level, player);
		boolean onTee = ball != null && ball.isOnTee();
		ShotAdvisor.Suggestion s = ShotAdvisor.suggest(distance, rise, onTee, state.is(LevelSurfaceLookup.GREEN));

		int up = (int) Math.round(rise);
		Component height = up > 0 ? Component.translatable("minegolf.hud.up", up)
				: up < 0 ? Component.translatable("minegolf.hud.down", -up)
				: Component.translatable("minegolf.hud.level");
		Component club = Component.translatable("item.minegolf." + s.club().name().toLowerCase(Locale.ROOT));
		String power = Math.round(s.power() * 100) + "%" + (s.outOfRange() ? "+" : "");
		Component text = Component.translatable("minegolf.hud.range", Math.round(distance), height, club, power);
		return cup != null ? Component.literal("⚑ ").append(text) : text;
	}

	/** The cup the player is aiming at, directly or through its flag. */
	private static @Nullable BlockPos cupAt(Level level, BlockPos pos) {
		for (int i = 0; i <= 3; i++) {
			BlockPos p = pos.below(i);
			BlockState state = level.getBlockState(p);
			if (state.is(ModBlocks.CUP)) return p;
			if (!state.is(ModBlocks.FLAG)) return null;
		}
		return null;
	}
}
