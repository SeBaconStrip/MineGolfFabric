package net.colin.minegolf.network;

import net.colin.minegolf.course.Course;
import net.colin.minegolf.course.CourseSavedData;
import net.colin.minegolf.item.CourseBookItem;
import net.colin.minegolf.item.GolfBagItem;
import net.colin.minegolf.item.ModComponents;
import net.colin.minegolf.round.RoundManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Handles serverbound payloads. Every handler re-checks what the player holds and may do. */
public final class ServerNetworking {
	private ServerNetworking() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(ModPayloads.CycleClub.TYPE,
				(payload, context) -> GolfBagItem.cycleClub(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(ModPayloads.CreateCourse.TYPE,
				(payload, context) -> createCourse(context.player(), payload.name()));
		ServerPlayNetworking.registerGlobalReceiver(ModPayloads.CourseAction.TYPE,
				(payload, context) -> courseAction(context.player(), payload));
	}

	private static void createCourse(ServerPlayer player, String rawName) {
		String name = rawName.strip();
		if (name.isEmpty() || name.length() > Course.MAX_NAME_LENGTH) return;
		ItemStack book = CourseBookItem.heldBook(player, null);
		if (book.isEmpty()) return;
		Course course = CourseSavedData.get(player.level().getServer()).create(name, player.getUUID());
		book.set(ModComponents.COURSE_ID, course.id());
		player.sendSystemMessage(Component.translatable("minegolf.msg.course_created", name));
		CourseBookItem.sendView(player, course);
	}

	private static void courseAction(ServerPlayer player, ModPayloads.CourseAction action) {
		// The bound book must be in hand, so a copied packet cannot reach foreign courses
		if (CourseBookItem.heldBook(player, action.course()).isEmpty()) return;
		CourseSavedData data = CourseSavedData.get(player.level().getServer());
		Course course = data.get(action.course());
		if (course == null) return;

		if (action.action() == ModPayloads.CourseAction.START_ROUND) {
			player.closeContainer();
			RoundManager.get(player.level().getServer()).start(player, course);
			return;
		}
		if (!CourseBookItem.canEdit(player, course)) {
			player.sendOverlayMessage(Component.translatable("minegolf.msg.not_owner"));
			return;
		}
		switch (action.action()) {
			case ModPayloads.CourseAction.PAR_UP -> course.changePar(action.value(), 1);
			case ModPayloads.CourseAction.PAR_DOWN -> course.changePar(action.value(), -1);
			case ModPayloads.CourseAction.DELETE_LAST -> course.removeLastHole();
			case ModPayloads.CourseAction.ADD_HOLE -> {
				if (course.isFull()) {
					player.sendOverlayMessage(Component.translatable("minegolf.msg.course_full"));
				} else {
					CourseBookItem.startLink(player, course);
				}
				return;
			}
			default -> {
				return;
			}
		}
		data.setDirty();
		CourseBookItem.sendView(player, course);
	}
}
