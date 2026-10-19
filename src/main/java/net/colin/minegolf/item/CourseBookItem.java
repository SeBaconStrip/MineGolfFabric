package net.colin.minegolf.item;

import net.colin.minegolf.block.ModBlocks;
import net.colin.minegolf.course.Course;
import net.colin.minegolf.course.CourseSavedData;
import net.colin.minegolf.course.Hole;
import net.colin.minegolf.network.ModPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Creates and edits a course. A fresh book asks for a name; a bound book opens the course screen.
 * "Add hole" puts the player in link mode: click a tee box, then a cup.
 */
public class CourseBookItem extends Item {
	private static final long LINK_TIMEOUT_TICKS = 1200;

	/** Player → pending link. Server thread only. */
	private static final Map<UUID, Link> LINKS = new HashMap<>();

	private record Link(UUID course, @Nullable BlockPos tee, long startedAt) {
	}

	public CourseBookItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			if (LINKS.remove(player.getUUID()) != null) {
				player.sendOverlayMessage(Component.translatable("minegolf.msg.link_cancelled"));
			} else {
				openFor(serverPlayer, player.getItemInHand(hand));
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null || !LINKS.containsKey(player.getUUID())) return InteractionResult.PASS;
		if (!(player instanceof ServerPlayer serverPlayer) || !(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		Link link = LINKS.get(player.getUUID());
		if (player.isSecondaryUseActive() || level.getGameTime() - link.startedAt > LINK_TIMEOUT_TICKS) {
			LINKS.remove(player.getUUID());
			player.sendOverlayMessage(Component.translatable("minegolf.msg.link_cancelled"));
			return InteractionResult.SUCCESS;
		}

		CourseSavedData data = CourseSavedData.get(level.getServer());
		Course course = data.get(link.course);
		if (course == null) {
			LINKS.remove(player.getUUID());
			return InteractionResult.FAIL;
		}
		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);
		if (course.uses(pos)) {
			player.sendOverlayMessage(Component.translatable("minegolf.msg.block_in_use"));
			return InteractionResult.FAIL;
		}
		if (link.tee == null) {
			if (!state.is(ModBlocks.TEE_BOX)) {
				player.sendOverlayMessage(Component.translatable("minegolf.msg.click_tee"));
				return InteractionResult.FAIL;
			}
			LINKS.put(player.getUUID(), new Link(link.course, pos.immutable(), level.getGameTime()));
			player.sendOverlayMessage(Component.translatable("minegolf.msg.click_cup"));
			return InteractionResult.SUCCESS;
		}
		if (!state.is(ModBlocks.CUP)) {
			player.sendOverlayMessage(Component.translatable("minegolf.msg.click_cup"));
			return InteractionResult.FAIL;
		}
		course.addHole(new Hole(level.dimension(), link.tee, pos.immutable(), Hole.DEFAULT_PAR));
		data.setDirty();
		LINKS.remove(player.getUUID());
		player.sendSystemMessage(Component.translatable("minegolf.msg.hole_added", course.holes().size(), Hole.DEFAULT_PAR));
		sendView(serverPlayer, course);
		return InteractionResult.SUCCESS;
	}

	/** Shows the create screen for an unbound book, or the course screen for a bound one. */
	private static void openFor(ServerPlayer player, ItemStack book) {
		UUID id = book.get(ModComponents.COURSE_ID);
		if (id == null) {
			ServerPlayNetworking.send(player, ModPayloads.OpenCreateCourse.INSTANCE);
			return;
		}
		Course course = CourseSavedData.get(player.level().getServer()).get(id);
		if (course == null) {
			book.remove(ModComponents.COURSE_ID);
			player.sendOverlayMessage(Component.translatable("minegolf.msg.course_missing"));
			return;
		}
		sendView(player, course);
	}

	public static void sendView(ServerPlayer player, Course course) {
		List<ModPayloads.HoleLine> holes = new ArrayList<>();
		for (Hole h : course.holes()) {
			holes.add(new ModPayloads.HoleLine(h.par(), h.length(), h.isIntact(player.level().getServer())));
		}
		ServerPlayNetworking.send(player, new ModPayloads.CourseView(course.id(), course.name(), canEdit(player, course), holes));
	}

	public static boolean canEdit(ServerPlayer player, Course course) {
		return course.owner().equals(player.getUUID()) || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	/** Book in either hand, bound to the given course (or unbound when id is null). */
	public static ItemStack heldBook(Player player, @Nullable UUID id) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.getItem() instanceof CourseBookItem && Objects.equals(stack.get(ModComponents.COURSE_ID), id)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	public static void startLink(ServerPlayer player, Course course) {
		LINKS.put(player.getUUID(), new Link(course.id(), null, player.level().getGameTime()));
		player.sendOverlayMessage(Component.translatable("minegolf.msg.click_tee"));
	}

	public static void forget(UUID player) {
		LINKS.remove(player);
	}
}
