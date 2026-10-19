package net.colin.minegolf.client;

import net.colin.minegolf.course.Course;
import net.colin.minegolf.network.ModPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Asks for the name of a new course. */
public class CreateCourseScreen extends Screen {
	private EditBox name;
	private Button create;

	public CreateCourseScreen() {
		super(Component.translatable("minegolf.screen.create_course"));
	}

	@Override
	protected void init() {
		int cx = width / 2;
		name = new EditBox(font, cx - 100, height / 2 - 20, 200, 20, Component.translatable("minegolf.screen.course_name"));
		name.setMaxLength(Course.MAX_NAME_LENGTH);
		name.setResponder(value -> create.active = !value.isBlank());
		addRenderableWidget(name);
		create = addRenderableWidget(Button.builder(Component.translatable("minegolf.screen.create"), b -> submit())
				.bounds(cx - 100, height / 2 + 10, 98, 20).build());
		create.active = false;
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
				.bounds(cx + 2, height / 2 + 10, 98, 20).build());
		setInitialFocus(name);
	}

	private void submit() {
		String value = name.getValue().strip();
		if (value.isEmpty()) return;
		ClientPlayNetworking.send(new ModPayloads.CreateCourse(value));
		onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(font, title, width / 2, height / 2 - 45, 0xFFFFFFFF);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
