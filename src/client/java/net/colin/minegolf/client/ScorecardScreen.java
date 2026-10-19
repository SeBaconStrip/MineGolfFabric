package net.colin.minegolf.client;

import net.colin.minegolf.network.ModPayloads;
import net.colin.minegolf.round.RoundManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Final scorecard: one row per player, one column per hole, sorted by total. */
public class ScorecardScreen extends Screen {
	private static final int NAME_WIDTH = 80;
	private static final int CELL = 16;
	private static final int ROW = 13;

	private final ModPayloads.RoundEnd end;

	public ScorecardScreen(ModPayloads.RoundEnd end) {
		super(Component.translatable("minegolf.screen.scorecard", end.course()));
		this.end = end;
	}

	private int tableWidth() {
		return NAME_WIDTH + end.pars().length * CELL + 2 * 30;
	}

	@Override
	protected void init() {
		int y = 50 + (end.rows().size() + 2) * ROW + 12;
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 50, y, 100, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(font, title, width / 2, 20, 0xFFFFD34D);
		int left = width / 2 - tableWidth() / 2;
		int y = 50;
		int totalX = left + NAME_WIDTH + end.pars().length * CELL;

		graphics.text(font, Component.translatable("minegolf.screen.hole"), left, y, 0xFFB0B0B0);
		for (int h = 0; h < end.pars().length; h++) {
			graphics.centeredText(font, String.valueOf(h + 1), left + NAME_WIDTH + h * CELL + CELL / 2, y, 0xFFB0B0B0);
		}
		graphics.text(font, Component.translatable("minegolf.screen.total"), totalX + 4, y, 0xFFB0B0B0);
		y += ROW;

		int parTotal = 0;
		graphics.text(font, Component.translatable("minegolf.screen.par"), left, y, 0xFFB0B0B0);
		for (int h = 0; h < end.pars().length; h++) {
			parTotal += end.pars()[h];
			String par = end.pars()[h] == 0 ? "-" : String.valueOf(end.pars()[h]);
			graphics.centeredText(font, par, left + NAME_WIDTH + h * CELL + CELL / 2, y, 0xFFB0B0B0);
		}
		graphics.text(font, String.valueOf(parTotal), totalX + 4, y, 0xFFB0B0B0);
		y += ROW;
		graphics.fill(left, y - 3, left + tableWidth(), y - 2, 0x80FFFFFF);

		for (ModPayloads.ScoreRow row : end.rows()) {
			graphics.text(font, row.name(), left, y, 0xFFFFFFFF);
			for (int h = 0; h < row.strokes().length; h++) {
				int par = end.pars()[h];
				int strokes = row.strokes()[h];
				int color = par == 0 ? 0xFF808080 : strokes < par ? 0xFF6CE06C : strokes > par ? 0xFFE07B6C : 0xFFFFFFFF;
				graphics.centeredText(font, String.valueOf(strokes), left + NAME_WIDTH + h * CELL + CELL / 2, y, color);
			}
			graphics.text(font, row.total() + " (" + RoundManager.formatToPar(row.toPar()) + ")", totalX + 4, y, 0xFFFFD34D);
			y += ROW;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
