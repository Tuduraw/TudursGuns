package com.example.tudursguns.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

/** Extra data carried on entity render states (Fabric's per-render-state data). */
public final class AimRenderState {

	private AimRenderState() {
	}

	/** TRUE while the player is aiming a handheld weapon; absent otherwise. */
	public static final RenderStateDataKey<Boolean> AIMING = RenderStateDataKey.create(() -> "tudursguns:aiming");

	/** Whether the living entity whose render state is currently being built is aiming. Set at the
	 * start of every LivingEntityRenderer.updateRenderState; the held items are resolved later in the
	 * same call (so ObjHandheldModelRenderer.getData can read it), before the next entity starts.
	 * Render thread only. */
	private static boolean entityBeingUpdatedAims;

	public static boolean entityBeingUpdatedAims() {
		return entityBeingUpdatedAims;
	}

	public static void setEntityBeingUpdatedAims(boolean aims) {
		entityBeingUpdatedAims = aims;
	}
}
