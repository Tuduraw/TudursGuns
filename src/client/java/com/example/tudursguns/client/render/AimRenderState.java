package com.example.tudursguns.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

/** Extra data carried on entity render states (Fabric's per-render-state data). */
public final class AimRenderState {

	private AimRenderState() {
	}

	/** TRUE while the player is aiming a handheld weapon; absent otherwise. */
	public static final RenderStateDataKey<Boolean> AIMING = RenderStateDataKey.create(() -> "tudursguns:aiming");
	/** TRUE while the player sprints carrying a handheld weapon (and isn't aiming); absent otherwise. */
	public static final RenderStateDataKey<Boolean> SPRINT_CARRY = RenderStateDataKey.create(() -> "tudursguns:sprint_carry");

	/** Whether the living entity whose render state is currently being built is aiming. Set at the
	 * start of every LivingEntityRenderer.updateRenderState; the held items are resolved later in the
	 * same call (so ObjHandheldModelRenderer.getData can read it), before the next entity starts.
	 * Render thread only. */
	private static boolean entityBeingUpdatedAims;
	private static boolean entityBeingUpdatedSprintCarries;

	public static boolean entityBeingUpdatedSprintCarries() {
		return entityBeingUpdatedSprintCarries;
	}

	public static void setEntityBeingUpdatedSprintCarries(boolean carries) {
		entityBeingUpdatedSprintCarries = carries;
	}

	public static boolean entityBeingUpdatedAims() {
		return entityBeingUpdatedAims;
	}

	public static void setEntityBeingUpdatedAims(boolean aims) {
		entityBeingUpdatedAims = aims;
	}
}
