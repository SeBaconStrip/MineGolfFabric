package net.colin.minegolf.item;

import net.colin.minegolf.block.ModBlocks;
import net.colin.minegolf.course.Course;
import net.colin.minegolf.course.CourseSavedData;
import net.colin.minegolf.course.Hole;
import net.colin.minegolf.course.HoleSigns;
import net.colin.minegolf.network.ModPayloads;
import net.colin.minegolf.stats.GolfAdvancements;
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

	/** A tee-then-cup link in progress; replace is the hole being relinked, or -1 for a new hole. */
	private record Link(UUID course, @Nullable BlockPos tee, long startedAt, int replace) {
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
		if (player == null) return InteractionResult.PASS;
		BlockState clicked = context.getLevel().getBlockState(context.getClickedPos());
		boolean marker = clicked.is(ModBlocks.TEE_BOX) || clicked.is(ModBlocks.CUP);
		// The client cannot see link mode, so it consumes every click on a marker block;
		// otherwise it would also send a use-in-air that cancels the link on the server
		if (!(player instanceof ServerPlayer serverPlayer) || !(context.getLevel() instanceof ServerLevel level)) {
			return marker ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		Link link = LINKS.get(player.getUUID());
		if (link == null) {
			if (!marker) return InteractionResult.PASS;
			openFor(serverPlayer, context.getItemInHand());
			return InteractionResult.SUCCESS;
		}
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
		if (course.uses(pos, link.replace)) {
			player.sendOverlayMessage(Component.translatable("minegolf.msg.block_in_use"));
			return InteractionResult.FAIL;
		}
		if (link.tee == null) {
			if (!state.is(ModBlocks.TEE_BOX)) {
				player.sendOverlayMessage(Component.translatable("minegolf.msg.click_tee"));
				return InteractionResult.FAIL;
			}
			LINKS.put(player.getUUID(), new Link(link.course, pos.immutable(), level.getGameTime(), link.replace));
			player.sendOverlayMessage(Component.translatable("minegolf.msg.click_cup"));
			return InteractionResult.SUCCESS;
		}
		if (!state.is(ModBlocks.CUP)) {
			player.sendOverlayMessage(Component.translatable("minegolf.msg.click_cup"));
			return InteractionResult.FAIL;
		}
		LINKS.remove(player.getUUID());
		List<BlockPos> tees = new ArrayList<>(HoleSigns.tees(level, course));
		tees.add(link.tee);
		if (link.replace >= 0) {
			if (link.replace >= course.holes().size()) {
				player.sendOverlayMessage(Component.translatable("minegolf.msg.link_cancelled"));
				return InteractionResult.FAIL;
			}
			int par = course.holes().get(link.replace).par();
			course.setHole(link.replace, new Hole(level.dimension(), link.tee, pos.immutable(), par));
			player.sendSystemMessage(Component.translatable("minegolf.msg.hole_relinked", link.replace + 1));
		} else {
			course.addHole(new Hole(level.dimension(), link.tee, pos.immutable(), Hole.DEFAULT_PAR));
			player.sendSystemMessage(Component.translatable("minegolf.msg.hole_added", course.holes().size(), Hole.DEFAULT_PAR));
		}
		data.setDirty();
		HoleSigns.refreshAround(level, tees);
		GolfAdvancements.award(serverPlayer, GolfAdvancements.COURSE_ARCHITECT);
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

	/** Starts linking a new hole (replace = -1) or relinking the tee and cup of an existing one. */
	public static void startLink(ServerPlayer player, Course course, int replace) {
		LINKS.put(player.getUUID(), new Link(course.id(), null, player.level().getGameTime(), replace));
		player.sendOverlayMessage(replace >= 0
				? Component.translatable("minegolf.msg.click_tee_relink", replace + 1)
				: Component.translatable("minegolf.msg.click_tee"));
	}

	public static void forget(UUID player) {
		LINKS.remove(player);
	}
}
