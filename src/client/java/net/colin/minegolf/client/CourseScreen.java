package net.colin.minegolf.client;

import net.colin.minegolf.network.ModPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Hole list of a course with par controls and the buttons to add holes and start a round. */
public class CourseScreen extends Screen {
	private static final int ROW_HEIGHT = 16;
	private static final int ROWS_PER_COLUMN = 9;
	private static final int COLUMN_WIDTH = 170;

	private final ModPayloads.CourseView view;

	public CourseScreen(ModPayloads.CourseView view) {
		super(Component.literal(view.name()));
		this.view = view;
	}

	private int columns() {
		return view.holes().size() > ROWS_PER_COLUMN ? 2 : 1;
	}

	private int left() {
		return width / 2 - columns() * COLUMN_WIDTH / 2;
	}

	private int top() {
		return 40;
	}

	@Override
	protected void init() {
		if (view.canEdit()) {
			for (int i = 0; i < view.holes().size(); i++) {
				int x = left() + (i / ROWS_PER_COLUMN) * COLUMN_WIDTH + COLUMN_WIDTH - 40;
				int y = top() + (i % ROWS_PER_COLUMN) * ROW_HEIGHT;
				int hole = i;
				addRenderableWidget(Button.builder(Component.literal("-"), b -> send(ModPayloads.CourseAction.PAR_DOWN, hole))
						.bounds(x, y, 14, 14).build());
				addRenderableWidget(Button.builder(Component.literal("+"), b -> send(ModPayloads.CourseAction.PAR_UP, hole))
						.bounds(x + 16, y, 14, 14).build());
			}
		}

		int y = top() + ROWS_PER_COLUMN * ROW_HEIGHT + 16;
		int cx = width / 2;
		if (view.canEdit()) {
			Button add = addRenderableWidget(Button.builder(Component.translatable("minegolf.screen.add_hole"), b -> {
				send(ModPayloads.CourseAction.ADD_HOLE, 0);
				onClose();
			}).bounds(cx - 154, y, 100, 20).build());
			add.active = view.holes().size() < 18;
			Button delete = addRenderableWidget(Button.builder(Component.translatable("minegolf.screen.delete_last"),
					b -> send(ModPayloads.CourseAction.DELETE_LAST, 0)).bounds(cx - 50, y, 100, 20).build());
			delete.active = !view.holes().isEmpty();
		}
		Button start = addRenderableWidget(Button.builder(Component.translatable("minegolf.screen.start_round"), b -> {
			send(ModPayloads.CourseAction.START_ROUND, 0);
			onClose();
		}).bounds(view.canEdit() ? cx + 54 : cx - 50, y, 100, 20).build());
		start.active = !view.holes().isEmpty();
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(cx - 50, y + 24, 100, 20).build());
	}

	private void send(int action, int value) {
		ClientPlayNetworking.send(new ModPayloads.CourseAction(view.id(), action, value));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(font, title, width / 2, 15, 0xFFFFD34D);
		if (view.holes().isEmpty()) {
			graphics.centeredText(font, Component.translatable("minegolf.screen.no_holes"), width / 2, top() + 20, 0xFFB0B0B0);
			return;
		}
		int total = 0;
		for (int i = 0; i < view.holes().size(); i++) {
			ModPayloads.HoleLine hole = view.holes().get(i);
			total += hole.par();
			int x = left() + (i / ROWS_PER_COLUMN) * COLUMN_WIDTH;
			int y = top() + (i % ROWS_PER_COLUMN) * ROW_HEIGHT + 3;
			Component line = Component.translatable("minegolf.screen.hole_line", i + 1, hole.par(), hole.length());
			graphics.text(font, line, x, y, hole.intact() ? 0xFFFFFFFF : 0xFFE0483A);
		}
		graphics.centeredText(font, Component.translatable("minegolf.screen.total_par", total),
				width / 2, top() + ROWS_PER_COLUMN * ROW_HEIGHT + 2, 0xFFB0B0B0);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
