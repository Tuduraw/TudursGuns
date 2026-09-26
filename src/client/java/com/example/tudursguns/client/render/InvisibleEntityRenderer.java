package com.example.tudursguns.client.render;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;

/** Draws nothing - for the smoke cloud (its particles are the visuals) and its lock-on decoys. */
public final class InvisibleEntityRenderer<T extends Entity> extends EntityRenderer<T, EntityRenderState> {

	public InvisibleEntityRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public EntityRenderState createRenderState() {
		return new EntityRenderState();
	}

	@Override
	public void render(EntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
	}
}
