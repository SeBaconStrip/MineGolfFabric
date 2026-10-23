package net.colin.minegolf.client;

import net.colin.minegolf.network.ModPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Hole list of a course. Owners can move, relink and delete each hole and change its par; everyone can
 * start a round.
 */
public class CourseScreen extends Screen {
	private static final int ROW_HEIGHT = 16;
	private static final int ROWS_PER_COLUMN = 9;
	private static final int COLUMN_WIDTH = 215;
	private static final int BUTTON = 14;

	private final ModPayloads.CourseView view;
	/** Hole whose delete button was clicked once and waits for the confirming second click. */
	private int confirmDelete = -1;

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
		int count = view.holes().size();
		if (view.canEdit()) {
			for (int i = 0; i < count; i++) {
				int x = left() + (i / ROWS_PER_COLUMN) * COLUMN_WIDTH + COLUMN_WIDTH - 6 * (BUTTON + 1) - 4;
				int y = top() + (i % ROWS_PER_COLUMN) * ROW_HEIGHT;
				int hole = i;
				small(x, y, "▲", "minegolf.screen.move_up", i > 0, () -> send(ModPayloads.CourseAction.MOVE_UP, hole));
				small(x + 15, y, "▼", "minegolf.screen.move_down", i < count - 1, () -> send(ModPayloads.CourseAction.MOVE_DOWN, hole));
				small(x + 30, y, "⟳", "minegolf.screen.relink", true, () -> {
					send(ModPayloads.CourseAction.RELINK, hole);
					onClose();
				});
				boolean confirming = confirmDelete == hole;
				small(x + 45, y, confirming ? "✔" : "✕", confirming ? "minegolf.screen.delete_confirm" : "minegolf.screen.delete", true, () -> {
					if (confirmDelete == hole) {
						send(ModPayloads.CourseAction.DELETE, hole);
					} else {
						confirmDelete = hole;
						rebuildWidgets();
					}
				});
				small(x + 60, y, "-", null, true, () -> send(ModPayloads.CourseAction.PAR_DOWN, hole));
				small(x + 75, y, "+", null, true, () -> send(ModPayloads.CourseAction.PAR_UP, hole));
			}
		}

		int y = top() + ROWS_PER_COLUMN * ROW_HEIGHT + 16;
		int cx = width / 2;
		if (view.canEdit()) {
			Button add = addRenderableWidget(Button.builder(Component.translatable("minegolf.screen.add_hole"), b -> {
				send(ModPayloads.CourseAction.ADD_HOLE, 0);
				onClose();
			}).bounds(cx - 104, y, 100, 20).build());
			add.active = count < 18;
		}
		Button start = addRenderableWidget(Button.builder(Component.translatable("minegolf.screen.start_round"), b -> {
			send(ModPayloads.CourseAction.START_ROUND, 0);
			onClose();
		}).bounds(view.canEdit() ? cx + 4 : cx - 50, y, 100, 20).build());
		start.active = !view.holes().isEmpty();
		boolean inRound = ClientRoundState.get() != null;
		if (inRound) {
			addRenderableWidget(Button.builder(Component.translatable("minegolf.screen.leave_round"), b -> {
				send(ModPayloads.CourseAction.LEAVE_ROUND, 0);
				onClose();
			}).bounds(cx - 104, y + 24, 100, 20).build());
		}
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(inRound ? cx + 4 : cx - 50, y + 24, 100, 20).build());
	}

	private void small(int x, int y, String label, String tooltip, boolean active, Runnable onPress) {
		Button.Builder builder = Button.builder(Component.literal(label), b -> onPress.run()).bounds(x, y, BUTTON, BUTTON);
		if (tooltip != null) builder.tooltip(Tooltip.create(Component.translatable(tooltip)));
		addRenderableWidget(builder.build()).active = active;
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
		boolean broken = false;
		for (int i = 0; i < view.holes().size(); i++) {
			ModPayloads.HoleLine hole = view.holes().get(i);
			total += hole.par();
			broken |= !hole.intact();
			int x = left() + (i / ROWS_PER_COLUMN) * COLUMN_WIDTH;
			int y = top() + (i % ROWS_PER_COLUMN) * ROW_HEIGHT + 3;
			Component line = Component.translatable("minegolf.screen.hole_line", i + 1, hole.par(), hole.length());
			graphics.text(font, line, x, y, hole.intact() ? 0xFFFFFFFF : 0xFFE0483A);
		}
		graphics.centeredText(font, Component.translatable("minegolf.screen.total_par", total),
				width / 2, top() + ROWS_PER_COLUMN * ROW_HEIGHT + 2, 0xFFB0B0B0);
		if (broken && view.canEdit()) {
			graphics.centeredText(font, Component.translatable("minegolf.screen.broken_hint"), width / 2, top() - 14, 0xFFE0483A);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
