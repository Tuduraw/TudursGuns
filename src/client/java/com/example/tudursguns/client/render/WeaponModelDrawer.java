package com.example.tudursguns.client.render;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.handheld.AttachmentDefinition;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.HandheldDefinitions;
import com.example.tudursvehiclemod.client.render.DitherCutoutLayers;
import com.example.tudursvehiclemod.client.render.ObjModel;
import com.example.tudursvehiclemod.client.render.ObjModelLoader;
import com.example.tudursvehiclemod.client.render.VehicleEntityRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Draws OBJ models (weapons with their attachments, attachment items) through Tudur's Vehicle Mod's
 * OBJ loader and render layer. */
public final class WeaponModelDrawer {

	private WeaponModelDrawer() {
	}

	/** Only log each missing model once rather than every frame. */
	private static final Set<Identifier> REPORTED_MISSING = new HashSet<>();

	/** The weapon's own model (minus groups hidden by its attachments), then each fitted attachment
	 * that has a model of its own, at its mount transform. */
	public static void drawWeapon(OrderedRenderCommandQueue queue, MatrixStack matrices, HandheldDefinition def,
			Map<String, Identifier> fitted, int light, int overlay) {
		if (def.model().isEmpty() || def.texture().isEmpty()) {
			return;
		}
		drawObj(queue, matrices, def.model().get(), def.texture().get(), def.hiddenGroups(fitted), light, overlay);
		for (Map.Entry<String, Identifier> entry : fitted.entrySet()) {
			HandheldDefinition.AttachmentMount mount = def.mountFor(entry.getKey(), entry.getValue());
			if (mount == null || mount.model().isEmpty()) {
				continue;
			}
			AttachmentDefinition attachment = HandheldDefinitions.getAnyAttachment(entry.getValue());
			Identifier texture = def.texture().get();
			if (mount.texture().isPresent()) {
				texture = mount.texture().get();
			} else if (attachment != null && attachment.texture().isPresent()) {
				texture = attachment.texture().get();
			}
			matrices.push();
			applyTransform(matrices, mount.transform());
			drawObj(queue, matrices, mount.model().get(), texture, Set.of(), light, overlay);
			matrices.pop();
		}
	}

	public static void drawObj(OrderedRenderCommandQueue queue, MatrixStack matrices, Identifier modelId, Identifier texture,
			Set<String> hiddenGroups, int light, int overlay) {
		ObjModel model = ObjModelLoader.get(modelId).orElse(null);
		if (model == null) {
			if (REPORTED_MISSING.add(modelId)) {
				TudursGuns.LOGGER.warn("OBJ model {} was not found", modelId);
			}
			return;
		}
		ObjModel.Triangles triangles = hiddenGroups.isEmpty() ? model.getTriangles() : model.getTrianglesExcluding(hiddenGroups);
		VehicleEntityRenderer.renderTriangles(queue, matrices, DitherCutoutLayers.entityDitherCutout(texture),
				triangles, light, overlay, 0xFFFFFFFF);
	}

	/** Translate, then rotate X, Y, Z (degrees), then scale. */
	public static void applyTransform(MatrixStack matrices, HandheldDefinition.DisplayTransform transform) {
		matrices.translate(transform.translation().x(), transform.translation().y(), transform.translation().z());
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(transform.rotation().x()));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(transform.rotation().y()));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(transform.rotation().z()));
		matrices.scale(transform.scale().x(), transform.scale().y(), transform.scale().z());
	}
}
