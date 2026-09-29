package com.example.tudursguns.client.render;

import net.minecraft.client.util.math.MatrixStack;
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

	public static void capture(MatrixStack matrices) {
		MatrixStack.Entry entry = matrices.peek();
		POSITION.set(entry.getPositionMatrix());
		NORMAL.set(entry.getNormalMatrix());
		captured = true;
	}

	/** Replaces the top of the stack with the captured base (identity if nothing was captured). */
	public static void apply(MatrixStack matrices) {
		MatrixStack.Entry entry = matrices.peek();
		if (captured) {
			entry.getPositionMatrix().set(POSITION);
			entry.getNormalMatrix().set(NORMAL);
		} else {
			entry.getPositionMatrix().identity();
			entry.getNormalMatrix().identity();
		}
	}
}
