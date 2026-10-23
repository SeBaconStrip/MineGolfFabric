package net.colin.minegolf;

import net.colin.minegolf.block.ModBlocks;
import net.colin.minegolf.command.MineGolfCommand;
import net.colin.minegolf.entity.ModEntities;
import net.colin.minegolf.item.CourseBookItem;
import net.colin.minegolf.item.ModComponents;
import net.colin.minegolf.item.ModItems;
import net.colin.minegolf.network.ModPayloads;
import net.colin.minegolf.network.ServerNetworking;
import net.colin.minegolf.round.RoundManager;
import net.colin.minegolf.stats.GolfStats;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MineGolf implements ModInitializer {
	public static final String MOD_ID = "minegolf";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing MineGolf");
		GolfStats.register();
		ModComponents.register();
		ModBlocks.register();
		ModItems.register();
		ModEntities.register();
		ModPayloads.register();
		ServerNetworking.register();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				MineGolfCommand.register(dispatcher));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			RoundManager.get(server).leave(handler.getPlayer());
			CourseBookItem.forget(handler.getPlayer().getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> RoundManager.reset());
	}
}
