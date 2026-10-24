package net.colin.minegolf.entity;

import net.colin.minegolf.block.ModBlocks;
import net.colin.minegolf.course.StakeIndex;
import net.colin.minegolf.item.ModItems;
import net.colin.minegolf.physics.BallPhysics;
import net.colin.minegolf.physics.BallState;
import net.colin.minegolf.physics.Club;
import net.colin.minegolf.physics.Surface;
import net.colin.minegolf.round.GolfRules;
import net.colin.minegolf.round.RoundManager;
import net.colin.minegolf.stats.GolfAdvancements;
import net.colin.minegolf.stats.GolfStats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** A golf ball lying in the world. The server runs {@link BallPhysics}; clients only render it. */
public class GolfBallEntity extends Entity implements ItemSupplier {
	public static final double HIT_RANGE = 2.5;
	public static final int PRACTICE = -1;

	private static final EntityDataAccessor<ItemStack> DATA_ITEM =
			SynchedEntityData.defineId(GolfBallEntity.class, EntityDataSerializers.ITEM_STACK);
	private static final EntityDataAccessor<Boolean> DATA_RESTING =
			SynchedEntityData.defineId(GolfBallEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<String> DATA_OWNER =
			SynchedEntityData.defineId(GolfBallEntity.class, EntityDataSerializers.STRING);

	private final BallState state = new BallState();
	private boolean stateSynced;
	private double restX, restY, restZ;
	private int roundId = PRACTICE;
	/** The shot in flight, for drive length and the bunker achievement. Null while resting. */
	private @Nullable Shot shot;

	private record Shot(Club club, Surface lie, double x, double z) {
	}

	public GolfBallEntity(EntityType<? extends GolfBallEntity> type, Level level) {
		super(type, level);
	}

	/** Spawns a ball resting at the given spot. */
	public static GolfBallEntity place(ServerLevel level, Player owner, ItemStack item, double x, double y, double z, int roundId) {
		GolfBallEntity ball = new GolfBallEntity(ModEntities.GOLF_BALL, level);
		ball.setItem(item);
		ball.entityData.set(DATA_OWNER, owner.getUUID().toString());
		ball.setCustomName(owner.getName());
		ball.setCustomNameVisible(true);
		ball.roundId = roundId;
		ball.setPos(x, y, z);
		ball.syncStateFromPosition();
		ball.markRest();
		level.addFreshEntity(ball);
		return ball;
	}

	/** Own resting ball within reach, if any. On the client, ownership is read from synced data too. */
	public static @Nullable GolfBallEntity findHittable(Level level, Player player) {
		List<GolfBallEntity> balls = level.getEntitiesOfClass(GolfBallEntity.class,
				player.getBoundingBox().inflate(HIT_RANGE), b -> b.isOwnedBy(player) && b.isResting());
		GolfBallEntity best = null;
		double bestDist = HIT_RANGE * HIT_RANGE;
		for (GolfBallEntity b : balls) {
			double d = b.distanceToSqr(player);
			if (d <= bestDist) {
				best = b;
				bestDist = d;
			}
		}
		return best;
	}

	/** All balls of a player in a wide area, used to replace an old practice ball. */
	public static List<GolfBallEntity> findOwned(Level level, Player player, double radius) {
		return level.getEntitiesOfClass(GolfBallEntity.class, player.getBoundingBox().inflate(radius), b -> b.isOwnedBy(player));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_ITEM, new ItemStack(ModItems.GOLF_BALL));
		builder.define(DATA_RESTING, true);
		builder.define(DATA_OWNER, "");
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel level) serverTick(level);
	}

	private void serverTick(ServerLevel level) {
		if (!stateSynced) syncStateFromPosition();
		LevelSurfaceLookup lookup = new LevelSurfaceLookup(level);

		// Ground broken away under a resting ball: let it fall
		if (state.resting() && tickCount % 10 == 0 && BallPhysics.supportTop(lookup, state.x, state.y, state.z) < state.y - 1e-6) {
			state.restTicks = 0;
			state.onGround = false;
		}
		if (state.resting()) return;

		BallPhysics.Event event = BallPhysics.step(state, lookup);
		setPos(state.x, state.y, state.z);
		setDeltaMovement(state.vx, state.vy, state.vz);
		switch (event) {
			case AT_REST -> {
				if (shot != null && StakeIndex.isOut(level, shot.x, shot.z, state.x, state.z)) {
					shot = null;
					level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8F, 0.6F);
					resetToRest();
					RoundManager.get(level.getServer()).onPenalty(this, "minegolf.msg.out_of_bounds",
							"minegolf.msg.practice_out_of_bounds");
					return;
				}
				markRest();
				shotCameToRest(lookup);
				RoundManager.get(level.getServer()).onBallRest(this);
			}
			case HOLED -> holed(level);
			case HAZARD -> {
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.6F, 1.2F);
				hitHazard(level);
				resetToRest();
				RoundManager.get(level.getServer()).onPenalty(this);
			}
			case STUCK -> {
				shot = null;
				resetToRest();
			}
			default -> {
			}
		}
	}

	private void shotCameToRest(LevelSurfaceLookup lookup) {
		Shot done = shot;
		shot = null;
		ServerPlayer owner = ownerPlayer();
		if (done == null || owner == null) return;
		if (done.club == Club.DRIVER) {
			int blocks = (int) Math.round(horizontalDistance(done));
			GolfStats.recordDrive(owner, blocks);
			if (blocks >= GolfRules.LONG_DRIVE_DISTANCE) GolfAdvancements.award(owner, GolfAdvancements.LONG_DRIVE);
		}
		if (BallPhysics.surfaceUnder(state, lookup) == Surface.BUNKER) GolfAdvancements.award(owner, GolfAdvancements.BEACH_DAY);
	}

	private void hitHazard(ServerLevel level) {
		shot = null;
		ServerPlayer owner = ownerPlayer();
		if (owner == null) return;
		FluidState fluid = level.getFluidState(BlockPos.containing(getX(), getY(), getZ()));
		if (fluid.is(FluidTags.LAVA)) {
			GolfAdvancements.award(owner, GolfAdvancements.HOT_SHOT);
			return;
		}
		// Falling out of the world is a hazard too, but no splash
		if (!fluid.is(FluidTags.WATER)) return;
		owner.awardStat(GolfStats.BALLS_IN_WATER);
		GolfAdvancements.award(owner, GolfAdvancements.SPLASH);
		if (GolfStats.get(owner, GolfStats.BALLS_IN_WATER) >= GolfAdvancements.FISH_FOOD_BALLS) {
			GolfAdvancements.award(owner, GolfAdvancements.FISH_FOOD);
		}
	}

	private double horizontalDistance(Shot from) {
		double dx = getX() - from.x;
		double dz = getZ() - from.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private void holed(ServerLevel level) {
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 1.6F);
		ServerPlayer shooter = ownerPlayer();
		if (shot != null && shooter != null && shot.lie == Surface.BUNKER
				&& horizontalDistance(shot) >= GolfRules.SAND_WIZARD_MIN_DISTANCE) {
			GolfAdvancements.award(shooter, GolfAdvancements.SAND_WIZARD);
		}
		shot = null;
		if (roundId == PRACTICE) {
			ServerPlayer owner = ownerPlayer();
			if (owner != null) owner.sendOverlayMessage(Component.translatable("minegolf.msg.practice_holed"));
			returnToOwner();
			discard();
		} else {
			RoundManager.get(level.getServer()).onHoled(this);
		}
	}

	/** Strikes the ball. Caller has checked ownership, reach and that the ball rests. */
	public void hit(Club club, double power, float yaw) {
		if (!stateSynced) syncStateFromPosition();
		Surface lie = BallPhysics.surfaceUnder(state, new LevelSurfaceLookup(level()));
		shot = new Shot(club, lie, state.x, state.z);
		BallPhysics.launch(state, club, power, yaw, lie);
		entityData.set(DATA_RESTING, false);
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, 1.6F);
	}

	private void markRest() {
		restX = state.x;
		restY = state.y;
		restZ = state.z;
		state.vx = state.vy = state.vz = 0;
		state.onGround = true;
		state.restTicks = BallPhysics.REST_TICKS;
		entityData.set(DATA_RESTING, true);
	}

	/** Puts the ball back where it last lay still (after a hazard or when stuck). */
	public void resetToRest() {
		state.x = restX;
		state.y = restY;
		state.z = restZ;
		setPos(restX, restY, restZ);
		markRest();
	}

	/** Starts physics from the entity position, which also becomes the spot hazards reset to. */
	private void syncStateFromPosition() {
		state.x = restX = getX();
		state.y = restY = getY();
		state.z = restZ = getZ();
		state.onGround = true;
		stateSynced = true;
	}

	public void returnToOwner() {
		ItemStack stack = getItem().copy();
		ServerPlayer owner = ownerPlayer();
		if (owner != null) {
			if (!owner.getInventory().add(stack)) owner.drop(stack, false, Prediction.SERVER_ONLY);
		} else if (level() instanceof ServerLevel level) {
			spawnAtLocation(level, stack);
		}
	}

	public @Nullable ServerPlayer ownerPlayer() {
		UUID owner = ownerId();
		if (owner == null || !(level() instanceof ServerLevel level)) return null;
		return level.getServer().getPlayerList().getPlayer(owner);
	}

	public @Nullable UUID ownerId() {
		String raw = entityData.get(DATA_OWNER);
		if (raw.isEmpty()) return null;
		try {
			return UUID.fromString(raw);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public boolean isOwnedBy(Player player) {
		return player.getUUID().equals(ownerId());
	}

	public boolean isResting() {
		return entityData.get(DATA_RESTING);
	}

	/** The driver may only be used while the ball sits on a tee box. */
	public boolean isOnTee() {
		BlockPos below = BlockPos.containing(getX(), getY() - 0.01, getZ());
		return level().getBlockState(below).is(ModBlocks.TEE_BOX);
	}

	public int roundId() {
		return roundId;
	}

	public BallState stateCopy() {
		BallState copy = state.copy();
		if (!stateSynced) {
			copy.x = getX();
			copy.y = getY();
			copy.z = getZ();
			copy.onGround = true;
		}
		return copy;
	}

	public void setItem(ItemStack stack) {
		entityData.set(DATA_ITEM, stack.copyWithCount(1));
	}

	@Override
	public ItemStack getItem() {
		return entityData.get(DATA_ITEM);
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		// Without sneaking the click falls through to the club in hand
		if (!player.isSecondaryUseActive() || !isOwnedBy(player)) return InteractionResult.PASS;
		if (!level().isClientSide()) {
			if (roundId != PRACTICE) {
				player.sendOverlayMessage(Component.translatable("minegolf.msg.no_pickup_in_round"));
				return InteractionResult.FAIL;
			}
			returnToOwner();
			discard();
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.store("Item", ItemStack.CODEC, getItem());
		UUID owner = ownerId();
		if (owner != null) output.store("Owner", UUIDUtil.CODEC, owner);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		setItem(input.read("Item", ItemStack.CODEC).orElseGet(() -> new ItemStack(ModItems.GOLF_BALL)));
		input.read("Owner", UUIDUtil.CODEC).ifPresent(id -> entityData.set(DATA_OWNER, id.toString()));
		// Rounds are not saved, so a reloaded ball is a practice ball
		roundId = PRACTICE;
		stateSynced = false;
	}

}
