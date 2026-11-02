package com.example.tudursguns.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;

/** Draws nothing - for entities with no look of their own (smoke cloud, lock-on decoys, laser spot). */
public final class InvisibleEntityRenderer<T extends Entity> extends EntityRenderer<T, EntityRenderState> {

	public InvisibleEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public EntityRenderState createRenderState() {
		return new EntityRenderState();
	}

	@Override
	public void submit(EntityRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState cameraState) {
	}
}
