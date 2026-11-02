package com.example.tudursguns.client.render;

import com.example.tudursguns.entity.MineEntity;
import com.example.tudursguns.handheld.MineDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Set;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

/** Draws a placed charge with its definition's OBJ model, standing out of the surface it's on (the
 * model's +Y along the surface normal) and turned to the way it faces (the model's +Z is the front -
 * the side a claymore fires to). The definition's "placed" transform is applied last. */
public class MineEntityRenderer extends EntityRenderer<MineEntity, MineEntityRenderer.State> {

	public static class State extends EntityRenderState {
		public Identifier mine;
		public float yaw;
		public Direction normal = Direction.UP;
	}

	public MineEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(MineEntity entity, State state, float tickProgress) {
		super.extractRenderState(entity, state, tickProgress);
		state.mine = entity.mineId();
		state.yaw = entity.getYRot();
		state.normal = entity.normal();
	}

	@Override
	public void submit(State state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState cameraState) {
		MineDefinition def = ModDefinitions.MINES.getAny(state.mine);
		if (def == null || def.model().isEmpty() || def.texture().isEmpty()) {
			return;
		}
		matrices.pushPose();
		// The entity's position is offset from the surface point for side and ceiling placements (so
		// its hitbox isn't in the block); move back to the surface point first.
		if (state.normal == Direction.DOWN) {
			matrices.translate(0.0, 0.2, 0.0);
		} else if (state.normal.getAxis().isHorizontal()) {
			matrices.translate(0.0, 0.1, 0.0);
		}
		matrices.mulPose(Axis.YP.rotationDegrees(-state.yaw));
		if (state.normal == Direction.DOWN) {
			matrices.mulPose(Axis.XP.rotationDegrees(180f));
		} else if (state.normal.getAxis().isHorizontal()) {
			// Model +Y onto the facing (which for a wall placement is the wall's normal).
			matrices.mulPose(Axis.XP.rotationDegrees(90f));
			matrices.translate(0.0, -0.1, 0.0);
		}
		WeaponModelDrawer.applyTransform(matrices, def.placed());
		WeaponModelDrawer.drawObj(queue, matrices, def.model().get(), def.texture().get(), Set.of(), state.lightCoords, OverlayTexture.NO_OVERLAY);
		matrices.popPose();
		super.submit(state, matrices, queue, cameraState);
	}
}
