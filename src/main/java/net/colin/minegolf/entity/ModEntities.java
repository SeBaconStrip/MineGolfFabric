package net.colin.minegolf.entity;

import net.colin.minegolf.MineGolf;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntities {
	private static final ResourceKey<EntityType<?>> GOLF_BALL_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MineGolf.MOD_ID, "golf_ball"));

	// Position updates every tick so other players see the ball fly smoothly
	public static final EntityType<GolfBallEntity> GOLF_BALL = Registry.register(BuiltInRegistries.ENTITY_TYPE, GOLF_BALL_KEY,
			EntityType.Builder.<GolfBallEntity>of(GolfBallEntity::new, MobCategory.MISC)
					.noLootTable()
					.sized(0.25F, 0.25F)
					.clientTrackingRange(10)
					.updateInterval(1)
					.build(GOLF_BALL_KEY));

	private static final ResourceKey<EntityType<?>> GOLF_CART_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MineGolf.MOD_ID, "golf_cart"));

	public static final EntityType<GolfCartEntity> GOLF_CART = Registry.register(BuiltInRegistries.ENTITY_TYPE, GOLF_CART_KEY,
			EntityType.Builder.<GolfCartEntity>of(GolfCartEntity::new, MobCategory.MISC)
					.noLootTable()
					.sized(1.4F, 1.0F)
					.clientTrackingRange(10)
					.build(GOLF_CART_KEY));

	public static void register() {
		MineGolf.LOGGER.debug("Registering entities");
	}
}
