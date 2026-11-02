package com.example.tudursguns.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** The pose vanilla starts first-person held-item rendering from (captured by FirstPersonBaseMixin at
 * the start of HeldItemRenderer.renderItem): the camera-relative frame, including whatever keeps the
 * hand in front of the view as the camera turns, plus view bobbing - but before any hand placement.
 * A weapon's first-person pose is built on top of this. Render thread only. */
public final class FirstPersonBase {

	private FirstPersonBase() {
	}

	private static final Matrix4f POSITION = new Matrix4f();
	private static final Matrix3f NORMAL = new Matrix3f();
	private static boolean captured;

	public static void capture(PoseStack matrices) {
		PoseStack.Pose entry = matrices.last();
		POSITION.set(entry.pose());
		NORMAL.set(entry.normal());
		captured = true;
	}

	/** Replaces the top of the stack with the captured base (identity if nothing was captured). */
	public static void apply(PoseStack matrices) {
		PoseStack.Pose entry = matrices.last();
		if (captured) {
			entry.pose().set(POSITION);
			entry.normal().set(NORMAL);
		} else {
			entry.pose().identity();
			entry.normal().identity();
		}
	}
}
