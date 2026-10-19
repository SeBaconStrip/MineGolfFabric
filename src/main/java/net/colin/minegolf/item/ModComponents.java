package net.colin.minegolf.item;

import net.colin.minegolf.MineGolf;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public class ModComponents {
	/** Course a Course Book is bound to. */
	public static final DataComponentType<UUID> COURSE_ID = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Identifier.fromNamespaceAndPath(MineGolf.MOD_ID, "course_id"),
			DataComponentType.<UUID>builder().persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC).build());

	public static void register() {
		MineGolf.LOGGER.debug("Registering data components");
	}
}
