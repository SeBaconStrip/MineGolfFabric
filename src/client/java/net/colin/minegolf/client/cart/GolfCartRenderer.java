package net.colin.minegolf.client.cart;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.colin.minegolf.MineGolf;
import net.colin.minegolf.entity.GolfCartEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class GolfCartRenderer extends EntityRenderer<GolfCartEntity, GolfCartRenderState> {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MineGolf.MOD_ID, "textures/entity/golf_cart.png");

	private final GolfCartModel model;

	public GolfCartRenderer(EntityRendererProvider.Context context) {
		super(context);
		model = new GolfCartModel(context.bakeLayer(GolfCartModel.LAYER));
		shadowRadius = 0.9F;
	}

	@Override
	public void submit(GolfCartRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		// Model pixels have y = 24 on the ground and grow downwards
		poseStack.translate(0.0F, 1.5F, 0.0F);
		poseStack.rotateDegrees(Axis.YP, 180.0F - state.yRot);
		if (state.hurtTime > 0) {
			poseStack.rotateDegrees(Axis.XP, Mth.sin(state.hurtTime) * state.hurtTime * state.damageTime / 10.0F * state.hurtDir);
		}
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		collector.submitModel(model, state, poseStack, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	public GolfCartRenderState createRenderState() {
		return new GolfCartRenderState();
	}

	@Override
	public void extractRenderState(GolfCartEntity cart, GolfCartRenderState state, float partialTicks) {
		super.extractRenderState(cart, state, partialTicks);
		state.yRot = cart.getYRot(partialTicks);
		state.hurtTime = cart.getHurtTime() - partialTicks;
		state.hurtDir = cart.getHurtDir();
		state.damageTime = Math.max(cart.getDamage() - partialTicks, 0.0F);
		state.wheelAngle = cart.wheelAngle(partialTicks);
		state.hasBag = cart.hasBag();
	}
}
