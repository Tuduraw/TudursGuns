package com.example.tudursguns.client.render;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.handheld.AnimationDefinition;
import com.example.tudursguns.handheld.AttachmentDefinition;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursvehiclemod.client.render.DitherCutoutLayers;
import com.example.tudursvehiclemod.client.render.ObjModel;
import com.example.tudursvehiclemod.client.render.ObjModelLoader;
import com.example.tudursvehiclemod.client.render.VehicleEntityRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Draws OBJ models and flat icons through Tudur's Vehicle Mod's OBJ loader and render layer. */
public final class WeaponModelDrawer {

	private WeaponModelDrawer() {
	}

	/** Only log each missing model once rather than every frame. */
	private static final Set<Identifier> REPORTED_MISSING = new HashSet<>();

	/** The weapon's own model (minus groups hidden by its attachments), then each fitted attachment
	 * that has a model of its own, at its mount transform - with animated parts posed (pose may be null
	 * for none). The pose's root offset is NOT applied here - the caller applies it first (in first
	 * person the arms follow it too). Every group belonging to a part is drawn at that part's chain of
	 * offsets; the rest move with the root. */
	public static void drawWeapon(OrderedRenderCommandQueue queue, MatrixStack matrices, HandheldDefinition def,
			Map<String, Identifier> fitted, int light, int overlay, WeaponPose pose) {
		if (def.model().isEmpty() || def.texture().isEmpty()) {
			return;
		}
		Set<String> hidden = def.hiddenGroups(fitted);
		if (pose == null) {
			drawObj(queue, matrices, def.model().get(), def.texture().get(), hidden, light, overlay);
		} else {
			Set<String> rootExcluded = new HashSet<>(hidden);
			for (Map.Entry<String, AnimationDefinition.Part> part : pose.animation().parts().entrySet()) {
				if (!AnimationDefinition.ROOT.equals(part.getKey())) {
					rootExcluded.addAll(part.getValue().groups());
				}
			}
			drawObj(queue, matrices, def.model().get(), def.texture().get(), rootExcluded, light, overlay);
			for (Map.Entry<String, AnimationDefinition.Part> part : pose.animation().parts().entrySet()) {
				if (AnimationDefinition.ROOT.equals(part.getKey()) || part.getValue().groups().isEmpty()) {
					continue;
				}
				matrices.push();
				pose.applyChain(matrices, part.getKey());
				drawGroups(queue, matrices, def.model().get(), def.texture().get(), part.getValue().groups(), hidden, light, overlay);
				matrices.pop();
			}
		}
		for (Map.Entry<String, Identifier> entry : fitted.entrySet()) {
			HandheldDefinition.AttachmentMount mount = def.mountFor(entry.getKey(), entry.getValue());
			if (mount == null || mount.model().isEmpty()) {
				continue;
			}
			AttachmentDefinition attachment = ModDefinitions.ATTACHMENTS.getAny(entry.getValue());
			Identifier texture = def.texture().get();
			if (mount.texture().isPresent()) {
				texture = mount.texture().get();
			} else if (attachment != null && attachment.texture().isPresent()) {
				texture = attachment.texture().get();
			}
			matrices.push();
			if (pose != null && mount.part().isPresent()) {
				pose.applyChain(matrices, mount.part().get());
			}
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

	/** Item bounds: the unit cube item model space is built around. */
	public static void unitCube(Consumer<Vector3fc> consumer) {
		for (int x = 0; x <= 1; x++) {
			for (int y = 0; y <= 1; y++) {
				for (int z = 0; z <= 1; z++) {
					consumer.accept(new Vector3f(x, y, z));
				}
			}
		}
	}

	/** A unit square in the z = 0.5 plane facing +Z (towards the viewer in inventories), for icons. */
	private static ObjModel iconQuad;

	private static ObjModel iconQuad() {
		if (iconQuad == null) {
			String obj = "v 0 0 0.5\nv 1 0 0.5\nv 1 1 0.5\nv 0 1 0.5\n"
					+ "vt 0 0\nvt 1 0\nvt 1 1\nvt 0 1\nvn 0 0 1\n"
					+ "f 1/1/1 2/2/1 3/3/1 4/4/1\n";
			try {
				iconQuad = ObjModel.parse(new java.io.BufferedReader(new java.io.StringReader(obj)));
			} catch (java.io.IOException e) {
				throw new IllegalStateException(e);
			}
		}
		return iconQuad;
	}

	/** A flat picture filling the item's unit square (an item icon), textured with texture - a PNG
	 * found the same way as model textures (resource packs, or textures/vehicle/ in an addon folder). */
	public static void drawIcon(OrderedRenderCommandQueue queue, MatrixStack matrices, Identifier texture, int light, int overlay) {
		VehicleEntityRenderer.renderTriangles(queue, matrices, DitherCutoutLayers.entityDitherCutout(texture),
				iconQuad().getTriangles(), light, overlay, 0xFFFFFFFF);
	}

	/** Only the named groups of a model (skipping hidden ones), one after another. */
	private static void drawGroups(OrderedRenderCommandQueue queue, MatrixStack matrices, Identifier modelId, Identifier texture,
			List<String> groups, Set<String> hiddenGroups, int light, int overlay) {
		ObjModel model = ObjModelLoader.get(modelId).orElse(null);
		if (model == null) {
			return;
		}
		for (String group : groups) {
			if (hiddenGroups.contains(group)) {
				continue;
			}
			ObjModel.Triangles triangles = model.getGroup(group);
			if (!triangles.isEmpty()) {
				VehicleEntityRenderer.renderTriangles(queue, matrices, DitherCutoutLayers.entityDitherCutout(texture),
						triangles, light, overlay, 0xFFFFFFFF);
			}
		}
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
