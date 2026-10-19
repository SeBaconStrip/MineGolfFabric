package net.colin.minegolf;

import net.colin.minegolf.block.ModBlocks;
import net.colin.minegolf.item.ModComponents;
import net.colin.minegolf.item.ModItems;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MineGolf implements ModInitializer {
	public static final String MOD_ID = "minegolf";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing MineGolf");
		ModComponents.register();
		ModBlocks.register();
		ModItems.register();
	}
}
