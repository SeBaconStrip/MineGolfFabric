package net.colin.minegolf.entity;

import net.colin.minegolf.item.GolfBagItem;
import net.colin.minegolf.item.ModItems;
import net.colin.minegolf.physics.CartPhysics;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Two-seat golf cart with a holder for a golf bag. Like a boat, the driver's client moves it from the driver's
 * move keys ({@link CartPhysics}); without a driver the server lets it roll out and fall.
 */
public class GolfCartEntity extends VehicleEntity {
	public static final int SEATS = 2;
	/** Seat offset to the side; the driver sits on the left. */
	private static final double SEAT_SIDE = 0.32;
	private static final double SEAT_BACK = -0.12;
	/** Top of the seat cushion, 12 model pixels above the ground. */
	private static final double RIDE_HEIGHT = 0.72;
	private static final double GRAVITY = 0.08;
	private static final float MAX_LOOK_ASIDE = 105F;
	private static final int UNDERWATER_EJECT_TICKS = 60;
	private static final double WHEEL_RADIUS = 3 / 16.0;

	private static final EntityDataAccessor<Boolean> DATA_HAS_BAG =
			SynchedEntityData.defineId(GolfCartEntity.class, EntityDataSerializers.BOOLEAN);

	private final CartPhysics.State drive = new CartPhysics.State();
	private float deltaYaw;
	private ItemStack bag = ItemStack.EMPTY;
	private int underwaterTicks;
	/** Client only, for the turning wheels. */
	private float wheelAngle, wheelAngleO;

	public GolfCartEntity(EntityType<? extends GolfCartEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_HAS_BAG, false);
	}

	@Override
	public void tick() {
		if (getHurtTime() > 0) setHurtTime(getHurtTime() - 1);
		if (getDamage() > 0) setDamage(getDamage() - 1);
		super.tick();

		underwaterTicks = isEyeInFluid(FluidTags.WATER) ? underwaterTicks + 1 : 0;
		if (!level().isClientSide() && underwaterTicks >= UNDERWATER_EJECT_TICKS) ejectPassengers();

		if (isLocalInstanceAuthoritative()) {
			drive(getControllingPassenger() instanceof Player driver ? input(driver) : CartPhysics.Input.NONE);
		} else {
			setDeltaMovement(Vec3.ZERO);
			deltaYaw = 0;
		}
		applyEffectsFromBlocks();

		if (level().isClientSide()) {
			wheelAngleO = wheelAngle;
			double dx = getX() - xo, dz = getZ() - zo;
			double moved = Math.sqrt(dx * dx + dz * dz);
			Vec3 forward = Vec3.directionFromRotation(0, getYRot());
			double sign = Math.signum(dx * forward.x + dz * forward.z);
			wheelAngle += (float) (sign * moved / WHEEL_RADIUS);
		}
	}

	/** The driver's move keys as the client set them: zza forward/back, xxa positive for left. */
	private static CartPhysics.Input input(Player driver) {
		return new CartPhysics.Input(driver.zza > 0.1F, driver.zza < -0.1F, driver.xxa > 0.1F, driver.xxa < -0.1F);
	}

	private void drive(CartPhysics.Input in) {
		float before = getYRot();
		drive.yaw = before;
		CartPhysics.step(drive, in);
		deltaYaw = drive.yaw - before;
		setYRot(drive.yaw);

		Vec3 dir = Vec3.directionFromRotation(0, getYRot());
		double vy = onGround() ? 0 : getDeltaMovement().y - GRAVITY;
		if (isInWater()) vy = Math.max(vy, -0.05);
		setDeltaMovement(dir.x * drive.speed, vy, dir.z * drive.speed);
		move(MoverType.SELF, getDeltaMovement());
		// A wall stops the cart instead of letting it push on
		if (horizontalCollision && !minorHorizontalCollision) drive.speed = 0;
	}

	@Override
	public float maxUpStep() {
		return 1.0F;
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 3);
	}

	// ---------------------------------------------------------------- passengers

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		ItemStack held = player.getItemInHand(hand);
		if (held.getItem() instanceof GolfBagItem && !hasBag()) {
			if (!level().isClientSide()) {
				setBag(held.copyWithCount(1));
				held.shrink(1);
				playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1F, 1F);
			}
			return InteractionResult.SUCCESS;
		}
		if (player.isSecondaryUseActive()) {
			if (held.isEmpty() && hasBag()) {
				if (!level().isClientSide()) {
					player.setItemInHand(hand, bag);
					setBag(ItemStack.EMPTY);
					playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1F, 0.8F);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (underwaterTicks >= UNDERWATER_EJECT_TICKS) return InteractionResult.PASS;
		if (!level().isClientSide() && !player.startRiding(this)) return InteractionResult.PASS;
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().size() < SEATS && !isEyeInFluid(FluidTags.WATER);
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return getFirstPassenger() instanceof Player driver ? driver : super.getControllingPassenger();
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		double side = getPassengers().indexOf(passenger) == 0 ? SEAT_SIDE : -SEAT_SIDE;
		return new Vec3(side, RIDE_HEIGHT, SEAT_BACK).yRot(-getYRot() * Mth.DEG_TO_RAD);
	}

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
		super.positionRider(passenger, moveFunction);
		if (passenger.isLocalInstanceAuthoritative()) {
			passenger.setYRot(passenger.getYRot() + deltaYaw);
			passenger.setYHeadRot(passenger.getYHeadRot() + deltaYaw);
		}
		clampRotation(passenger);
	}

	@Override
	public void onPassengerTurned(Entity passenger) {
		passenger.yRotO += clampRotation(passenger);
	}

	/** Keeps a passenger's view within reach of the driving direction, like in a boat. */
	private float clampRotation(Entity passenger) {
		passenger.setYBodyRot(getYRot());
		float delta = Mth.wrapDegrees(passenger.getYRot() - getYRot());
		float clamped = Mth.clamp(delta, -MAX_LOOK_ASIDE, MAX_LOOK_ASIDE);
		passenger.setYRot(passenger.getYRot() + clamped - delta);
		return clamped - delta;
	}

	// ---------------------------------------------------------------- collisions

	@Override
	public boolean canCollideWith(Entity entity) {
		return (entity.canBeCollidedWith(this) || entity.isPushable()) && !isPassengerOfSameVehicle(entity);
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return true;
	}

	@Override
	public boolean isPushable() {
		return true;
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	// ---------------------------------------------------------------- bag

	public boolean hasBag() {
		return entityData.get(DATA_HAS_BAG);
	}

	/** The bag on the cart; the club key changes it in place. Empty when there is none. */
	public ItemStack bag() {
		return bag;
	}

	public void setBag(ItemStack stack) {
		bag = stack;
		entityData.set(DATA_HAS_BAG, !stack.isEmpty());
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		// Broken, also in creative: the bag must not vanish with the cart
		if (level() instanceof ServerLevel level && reason.shouldDestroy() && hasBag()) {
			spawnAtLocation(level, bag);
			setBag(ItemStack.EMPTY);
		}
		super.remove(reason);
	}

	@Override
	protected Item getDropItem() {
		return ModItems.GOLF_CART;
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(ModItems.GOLF_CART);
	}

	// ---------------------------------------------------------------- rendering

	public float wheelAngle(float partialTicks) {
		return Mth.lerp(partialTicks, wheelAngleO, wheelAngle);
	}

	// ---------------------------------------------------------------- saving

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		if (!bag.isEmpty()) output.store("Bag", ItemStack.CODEC, bag);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		setBag(input.read("Bag", ItemStack.CODEC).orElse(ItemStack.EMPTY));
	}

	/** Used by the item: sound when the cart is set down. */
	public void playPlaceSound() {
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1F, 0.8F);
	}
}
