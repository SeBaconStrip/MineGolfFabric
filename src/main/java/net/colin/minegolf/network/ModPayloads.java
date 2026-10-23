package net.colin.minegolf.network;

import net.colin.minegolf.MineGolf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Every packet MineGolf sends. Serverbound ones are intents the server re-validates. */
public final class ModPayloads {
	private ModPayloads() {
	}

	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String name) {
		return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MineGolf.MOD_ID, name));
	}

	// ------------------------------------------------------------- serverbound

	/** Swap the held club with the next club in the golf bag. */
	public record CycleClub() implements CustomPacketPayload {
		public static final CycleClub INSTANCE = new CycleClub();
		public static final Type<CycleClub> TYPE = payloadType("cycle_club");
		public static final StreamCodec<RegistryFriendlyByteBuf, CycleClub> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<CycleClub> type() {
			return TYPE;
		}
	}

	/** Name entered on the create-course screen for the unbound book in hand. */
	public record CreateCourse(String name) implements CustomPacketPayload {
		public static final Type<CreateCourse> TYPE = payloadType("create_course");
		public static final StreamCodec<RegistryFriendlyByteBuf, CreateCourse> CODEC = CustomPacketPayload.codec(
				(p, buf) -> buf.writeUtf(p.name, 64), buf -> new CreateCourse(buf.readUtf(64)));

		@Override
		public Type<CreateCourse> type() {
			return TYPE;
		}
	}

	/** A button on the course screen. {@code value} is the hole index where it matters. */
	public record CourseAction(UUID course, int action, int value) implements CustomPacketPayload {
		public static final int PAR_UP = 0;
		public static final int PAR_DOWN = 1;
		public static final int DELETE_LAST = 2;
		public static final int ADD_HOLE = 3;
		public static final int START_ROUND = 4;
		public static final int MOVE_UP = 5;
		public static final int MOVE_DOWN = 6;
		public static final int DELETE = 7;
		public static final int RELINK = 8;
		public static final int LEAVE_ROUND = 9;

		public static final Type<CourseAction> TYPE = payloadType("course_action");
		public static final StreamCodec<RegistryFriendlyByteBuf, CourseAction> CODEC = CustomPacketPayload.codec(
				(p, buf) -> {
					buf.writeUUID(p.course);
					buf.writeVarInt(p.action);
					buf.writeVarInt(p.value);
				},
				buf -> new CourseAction(buf.readUUID(), buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<CourseAction> type() {
			return TYPE;
		}
	}

	// ------------------------------------------------------------- clientbound

	public record OpenCreateCourse() implements CustomPacketPayload {
		public static final OpenCreateCourse INSTANCE = new OpenCreateCourse();
		public static final Type<OpenCreateCourse> TYPE = payloadType("open_create_course");
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenCreateCourse> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<OpenCreateCourse> type() {
			return TYPE;
		}
	}

	public record HoleLine(int par, int length, boolean intact) {
	}

	/** Opens or refreshes the course screen. */
	public record CourseView(UUID id, String name, boolean canEdit, List<HoleLine> holes) implements CustomPacketPayload {
		public static final Type<CourseView> TYPE = payloadType("course_view");
		public static final StreamCodec<RegistryFriendlyByteBuf, CourseView> CODEC = CustomPacketPayload.codec(
				(p, buf) -> {
					buf.writeUUID(p.id);
					buf.writeUtf(p.name, 64);
					buf.writeBoolean(p.canEdit);
					buf.writeVarInt(p.holes.size());
					for (HoleLine h : p.holes) {
						buf.writeVarInt(h.par);
						buf.writeVarInt(h.length);
						buf.writeBoolean(h.intact);
					}
				},
				buf -> {
					UUID id = buf.readUUID();
					String name = buf.readUtf(64);
					boolean canEdit = buf.readBoolean();
					int n = buf.readVarInt();
					List<HoleLine> holes = new ArrayList<>(n);
					for (int i = 0; i < n; i++) holes.add(new HoleLine(buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
					return new CourseView(id, name, canEdit, holes);
				});

		@Override
		public Type<CourseView> type() {
			return TYPE;
		}
	}

	public record PlayerLine(UUID id, String name, int strokes, boolean finished, int toPar) {
		static void write(FriendlyByteBuf buf, PlayerLine p) {
			buf.writeUUID(p.id);
			buf.writeUtf(p.name, 64);
			buf.writeVarInt(p.strokes);
			buf.writeBoolean(p.finished);
			buf.writeVarInt(p.toPar);
		}

		static PlayerLine read(FriendlyByteBuf buf) {
			return new PlayerLine(buf.readUUID(), buf.readUtf(64), buf.readVarInt(), buf.readBoolean(), buf.readVarInt());
		}
	}

	/** Current hole of the round the receiver plays in; drives the HUD. */
	public record RoundState(int roundId, String course, int hole, int holeCount, int par, BlockPos tee, BlockPos cup,
							 List<PlayerLine> players) implements CustomPacketPayload {
		public static final Type<RoundState> TYPE = payloadType("round_state");
		public static final StreamCodec<RegistryFriendlyByteBuf, RoundState> CODEC = CustomPacketPayload.codec(
				(p, buf) -> {
					buf.writeVarInt(p.roundId);
					buf.writeUtf(p.course, 64);
					buf.writeVarInt(p.hole);
					buf.writeVarInt(p.holeCount);
					buf.writeVarInt(p.par);
					buf.writeBlockPos(p.tee);
					buf.writeBlockPos(p.cup);
					buf.writeVarInt(p.players.size());
					for (PlayerLine line : p.players) PlayerLine.write(buf, line);
				},
				buf -> {
					int roundId = buf.readVarInt();
					String course = buf.readUtf(64);
					int hole = buf.readVarInt();
					int holeCount = buf.readVarInt();
					int par = buf.readVarInt();
					BlockPos tee = buf.readBlockPos();
					BlockPos cup = buf.readBlockPos();
					int n = buf.readVarInt();
					List<PlayerLine> players = new ArrayList<>(n);
					for (int i = 0; i < n; i++) players.add(PlayerLine.read(buf));
					return new RoundState(roundId, course, hole, holeCount, par, tee, cup, players);
				});

		@Override
		public Type<RoundState> type() {
			return TYPE;
		}
	}

	public record ScoreRow(String name, int[] strokes, int total, int toPar) {
	}

	/** Final scorecard after the last hole. */
	public record RoundEnd(String course, int[] pars, List<ScoreRow> rows) implements CustomPacketPayload {
		public static final Type<RoundEnd> TYPE = payloadType("round_end");
		public static final StreamCodec<RegistryFriendlyByteBuf, RoundEnd> CODEC = CustomPacketPayload.codec(
				(p, buf) -> {
					buf.writeUtf(p.course, 64);
					buf.writeVarIntArray(p.pars);
					buf.writeVarInt(p.rows.size());
					for (ScoreRow r : p.rows) {
						buf.writeUtf(r.name, 64);
						buf.writeVarIntArray(r.strokes);
						buf.writeVarInt(r.total);
						buf.writeVarInt(r.toPar);
					}
				},
				buf -> {
					String course = buf.readUtf(64);
					int[] pars = buf.readVarIntArray(32);
					int n = buf.readVarInt();
					List<ScoreRow> rows = new ArrayList<>(n);
					for (int i = 0; i < n; i++) {
						rows.add(new ScoreRow(buf.readUtf(64), buf.readVarIntArray(32), buf.readVarInt(), buf.readVarInt()));
					}
					return new RoundEnd(course, pars, rows);
				});

		@Override
		public Type<RoundEnd> type() {
			return TYPE;
		}
	}

	/** The receiver is no longer in a round; hide the HUD. */
	public record RoundClear() implements CustomPacketPayload {
		public static final RoundClear INSTANCE = new RoundClear();
		public static final Type<RoundClear> TYPE = payloadType("round_clear");
		public static final StreamCodec<RegistryFriendlyByteBuf, RoundClear> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<RoundClear> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry<RegistryFriendlyByteBuf> serverbound = PayloadTypeRegistry.serverboundPlay();
		serverbound.register(CycleClub.TYPE, CycleClub.CODEC);
		serverbound.register(CreateCourse.TYPE, CreateCourse.CODEC);
		serverbound.register(CourseAction.TYPE, CourseAction.CODEC);

		PayloadTypeRegistry<RegistryFriendlyByteBuf> clientbound = PayloadTypeRegistry.clientboundPlay();
		clientbound.register(OpenCreateCourse.TYPE, OpenCreateCourse.CODEC);
		clientbound.register(CourseView.TYPE, CourseView.CODEC);
		clientbound.register(RoundState.TYPE, RoundState.CODEC);
		clientbound.register(RoundEnd.TYPE, RoundEnd.CODEC);
		clientbound.register(RoundClear.TYPE, RoundClear.CODEC);
	}
}
