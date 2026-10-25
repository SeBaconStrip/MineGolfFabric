package net.colin.minegolf.client.cart;

import net.colin.minegolf.MineGolf;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;

/**
 * Golf cart in model pixels: ground at y = 24, front towards -z, the driver's side (left) towards +x.
 * The texture is split into single-colour zones of 128 × 64, so each cube just points at its zone.
 */
public class GolfCartModel extends EntityModel<GolfCartRenderState> {
	public static final ModelLayerLocation LAYER =
			new ModelLayerLocation(Identifier.fromNamespaceAndPath(MineGolf.MOD_ID, "golf_cart"), "main");

	private static final int[] BODY = {0, 0}, SEAT = {128, 0}, TYRE = {0, 64}, ROOF = {128, 64}, BAG = {0, 128}, METAL = {128, 128};
	private static final String[] WHEELS = {"wheel_front_left", "wheel_front_right", "wheel_rear_left", "wheel_rear_right"};

	private final ModelPart[] wheels = new ModelPart[WHEELS.length];
	private final ModelPart bag;

	public GolfCartModel(ModelPart root) {
		super(root);
		for (int i = 0; i < WHEELS.length; i++) wheels[i] = root.getChild(WHEELS[i]);
		bag = root.getChild("bag");
	}

	private static CubeListBuilder cube(int[] zone, float x, float y, float z, float w, float h, float d) {
		return CubeListBuilder.create().texOffs(zone[0], zone[1]).addBox(x, y, z, w, h, d);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("floor", cube(BODY, -10, 16, -16, 20, 3, 32), PartPose.ZERO);
		root.addOrReplaceChild("hood", cube(BODY, -10, 10, -16, 20, 6, 8), PartPose.ZERO);
		root.addOrReplaceChild("deck", cube(BODY, -10, 13, 8, 20, 3, 8), PartPose.ZERO);
		root.addOrReplaceChild("seat", cube(SEAT, -9, 12, -4, 18, 4, 10), PartPose.ZERO);
		root.addOrReplaceChild("seat_back", cube(SEAT, -9, 3, 5, 18, 9, 2), PartPose.ZERO);
		root.addOrReplaceChild("steering_column", cube(METAL, 4.5F, 9, -8, 1, 4, 1), PartPose.ZERO);
		root.addOrReplaceChild("steering_wheel", cube(TYRE, 2, 5, -7.5F, 6, 5, 1), PartPose.ZERO);
		float[][] posts = {{9, -9}, {-10, -9}, {9, 14}, {-10, 14}};
		for (int i = 0; i < posts.length; i++) {
			root.addOrReplaceChild("post_" + i, cube(METAL, posts[i][0], -9, posts[i][1], 1, 25, 1), PartPose.ZERO);
		}
		root.addOrReplaceChild("roof", cube(ROOF, -11, -10, -11, 22, 1, 27), PartPose.ZERO);
		float[][] wheelPos = {{10.5F, -10}, {-10.5F, -10}, {10.5F, 10}, {-10.5F, 10}};
		for (int i = 0; i < WHEELS.length; i++) {
			root.addOrReplaceChild(WHEELS[i], cube(TYRE, -1.5F, -3, -3, 3, 6, 6), PartPose.offset(wheelPos[i][0], 21, wheelPos[i][1]));
		}
		root.addOrReplaceChild("bag", CubeListBuilder.create()
				.texOffs(BAG[0], BAG[1]).addBox(-4, 1, 9, 8, 12, 5)
				.texOffs(METAL[0], METAL[1]).addBox(-3, -3, 10, 6, 4, 3), PartPose.ZERO);
		return LayerDefinition.create(mesh, 256, 256);
	}

	@Override
	public void setupAnim(GolfCartRenderState state) {
		super.setupAnim(state);
		for (ModelPart wheel : wheels) wheel.xRot = -state.wheelAngle;
		bag.visible = state.hasBag;
	}
}
