package net.colin.minegolf.client;

import net.colin.minegolf.item.ClubItem;
import net.colin.minegolf.network.ModPayloads;
import net.colin.minegolf.physics.Club;
import net.colin.minegolf.round.RoundManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Power bar while charging a swing, and the round panel in the top-left corner. */
public final class GolfHud {
	private static final int BAR_WIDTH = 102;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GOLD = 0xFFFFD34D;
	private static final int GREY = 0xFFB0B0B0;

	private GolfHud() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) return;
		powerBar(graphics, mc.font, player);
		roundPanel(graphics, mc.font, player);
		RangeFinderHud.extract(graphics, mc.font);
	}

	private static void powerBar(GuiGraphicsExtractor g, Font font, LocalPlayer player) {
		ItemStack using = player.getUseItem();
		if (!player.isUsingItem() || !(using.getItem() instanceof ClubItem)) return;
		int ticks = ClubItem.chargeTicks(player, using);
		double power = Club.powerForTicks(ticks);
		int x = g.guiWidth() / 2 - BAR_WIDTH / 2;
		int y = g.guiHeight() - 60;
		g.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + 8, 0xC0000000);
		int filled = (int) Math.round(power * BAR_WIDTH);
		g.fill(x, y, x + filled, y + 7, barColor(power, ticks));
		String label = ticks > Club.OVERCHARGE_TICKS
				? Component.translatable("minegolf.hud.too_late").getString()
				: Math.round(power * 100) + "%";
		g.centeredText(font, label, x + BAR_WIDTH / 2, y - 11, WHITE);
	}

	private static int barColor(double power, int ticks) {
		// Blink while at full power to warn that holding longer will waste the swing
		if (ticks > Club.CHARGE_TICKS && (ticks / 4) % 2 == 0) return 0xFFFFFFFF;
		if (power < 0.5) return 0xFF4CC34C;
		if (power < 0.85) return 0xFFE5C23B;
		return 0xFFE0483A;
	}

	private static void roundPanel(GuiGraphicsExtractor g, Font font, LocalPlayer player) {
		ModPayloads.RoundState state = ClientRoundState.get();
		if (state == null) return;

		ModPayloads.PlayerLine me = null;
		for (ModPayloads.PlayerLine line : state.players()) {
			if (line.id().equals(player.getUUID())) me = line;
		}
		boolean teedOff = me != null && (me.strokes() > 0 || me.finished());
		BlockPos target = teedOff ? state.cup() : state.tee();
		int distance = (int) Math.round(Math.sqrt(player.distanceToSqr(target.getX() + 0.5, player.getY(), target.getZ() + 0.5)));

		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("minegolf.hud.hole", state.hole(), state.holeCount(), state.par()));
		if (me != null) {
			lines.add(Component.translatable("minegolf.hud.strokes", me.strokes(), RoundManager.formatToPar(me.toPar())));
		}
		lines.add(Component.translatable(teedOff ? "minegolf.hud.to_cup" : "minegolf.hud.to_tee", distance));
		List<Component> others = new ArrayList<>();
		for (ModPayloads.PlayerLine line : state.players()) {
			if (me != null && line.id().equals(me.id())) continue;
			others.add(Component.literal((line.finished() ? "✔ " : "• ") + line.name() + "  " + line.strokes()));
		}

		int width = font.width(state.course());
		for (Component c : lines) width = Math.max(width, font.width(c));
		for (Component c : others) width = Math.max(width, font.width(c));
		int height = 12 + lines.size() * 10 + (others.isEmpty() ? 0 : 4 + others.size() * 10);
		g.fill(4, 4, 4 + width + 8, 4 + height, 0x90000000);

		int y = 8;
		g.text(font, state.course(), 8, y, GOLD);
		y += 12;
		for (Component c : lines) {
			g.text(font, c, 8, y, WHITE);
			y += 10;
		}
		if (!others.isEmpty()) y += 4;
		for (Component c : others) {
			g.text(font, c, 8, y, GREY);
			y += 10;
		}
	}
}
