package com.example.tudursguns.client.render;

import com.example.tudursguns.entity.MineEntity;
import com.example.tudursguns.handheld.MineDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

import java.util.Set;

/** Draws a placed charge with its definition's OBJ model, standing out of the surface it's on (the
 * model's +Y along the surface normal) and turned to the way it faces (the model's +Z is the front -
 * the side a claymore fires to). The definition's "placed" transform is applied last. */
public class MineEntityRenderer extends EntityRenderer<MineEntity, MineEntityRenderer.State> {

	public static class State extends EntityRenderState {
		public Identifier mine;
		public float yaw;
		public Direction normal = Direction.UP;
	}

	public MineEntityRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void updateRenderState(MineEntity entity, State state, float tickProgress) {
		super.updateRenderState(entity, state, tickProgress);
		state.mine = entity.mineId();
		state.yaw = entity.getYaw();
		state.normal = entity.normal();
	}

	@Override
	public void render(State state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
		MineDefinition def = ModDefinitions.MINES.getAny(state.mine);
		if (def == null || def.model().isEmpty() || def.texture().isEmpty()) {
			return;
		}
		matrices.push();
		// The entity's position is offset from the surface point for side and ceiling placements (so
		// its hitbox isn't in the block); move back to the surface point first.
		if (state.normal == Direction.DOWN) {
			matrices.translate(0.0, 0.2, 0.0);
		} else if (state.normal.getAxis().isHorizontal()) {
			matrices.translate(0.0, 0.1, 0.0);
		}
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-state.yaw));
		if (state.normal == Direction.DOWN) {
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180f));
		} else if (state.normal.getAxis().isHorizontal()) {
			// Model +Y onto the facing (which for a wall placement is the wall's normal).
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));
			matrices.translate(0.0, -0.1, 0.0);
		}
		WeaponModelDrawer.applyTransform(matrices, def.placed());
		WeaponModelDrawer.drawObj(queue, matrices, def.model().get(), def.texture().get(), Set.of(), state.light, OverlayTexture.DEFAULT_UV);
		matrices.pop();
		super.render(state, matrices, queue, cameraState);
	}
}
