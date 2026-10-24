package net.colin.minegolf.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.colin.minegolf.MineGolf;
import net.colin.minegolf.entity.ModEntities;
import net.colin.minegolf.network.ModPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.Identifier;

public class MineGolfClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MineGolf.MOD_ID, "minegolf"));

	@Override
	public void onInitializeClient() {
		EntityRenderers.register(ModEntities.GOLF_BALL, context -> new ThrownItemRenderer<>(context, 0.6F, false));
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MineGolf.MOD_ID, "hud"), GolfHud::extract);

		KeyMapping cycleClub = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.minegolf.cycle_club", InputConstants.KEY_R, CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (cycleClub.consumeClick()) {
				if (client.player != null) ClientPlayNetworking.send(ModPayloads.CycleClub.INSTANCE);
			}
			AimLine.tick(client);
			RangeFinderHud.tick(client);
		});

		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.OpenCreateCourse.TYPE,
				(payload, context) -> context.client().gui.setScreen(new CreateCourseScreen()));
		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.CourseView.TYPE,
				(payload, context) -> context.client().gui.setScreen(new CourseScreen(payload)));
		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.RoundState.TYPE,
				(payload, context) -> ClientRoundState.set(payload));
		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.RoundEnd.TYPE,
				(payload, context) -> context.client().gui.setScreen(new ScorecardScreen(payload)));
		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.RoundClear.TYPE,
				(payload, context) -> ClientRoundState.set(null));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientRoundState.set(null));
	}
}
