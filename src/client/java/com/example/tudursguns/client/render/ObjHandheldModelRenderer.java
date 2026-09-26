package com.example.tudursguns.client.render;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.HandheldDefinitions;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursvehiclemod.client.render.DitherCutoutLayers;
import com.example.tudursvehiclemod.client.render.ObjModel;
import com.example.tudursvehiclemod.client.render.ObjModelLoader;
import com.example.tudursvehiclemod.client.render.VehicleEntityRenderer;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** Draws a handheld weapon's OBJ model as its item model, through Tudur's Vehicle Mod's own OBJ
 * loader and render layer - so the same .obj/.png pair a vehicle would use works here, with the same
 * translucency setting.
 *
 * The item model JSON supplies the base display transform per context; the definition's own
 * "display" entry for that context is applied on top (see HandheldDefinition.DisplayTransform). */
public class ObjHandheldModelRenderer implements SpecialModelRenderer<Identifier> {

	public static final Identifier TYPE_ID = Identifier.of(TudursGuns.MOD_ID, "obj_handheld");

	/** Only log each missing model/texture once rather than every frame. */
	private static final Set<Identifier> REPORTED_MISSING = new HashSet<>();

	@Override
	public Identifier getData(ItemStack stack) {
		return stack.get(ModComponents.WEAPON);
	}

	@Override
	public void render(Identifier weaponId, ItemDisplayContext displayContext, MatrixStack matrices,
			OrderedRenderCommandQueue queue, int light, int overlay, boolean glint, int outlineColor) {
		if (weaponId == null) {
			return;
		}
		HandheldDefinitions.ClientEntry entry = HandheldDefinitions.getClient(weaponId);
		if (entry == null) {
			return;
		}
		HandheldDefinition def = entry.definition();
		if (def.model().isEmpty() || def.texture().isEmpty()) {
			return;
		}
		ObjModel model = ObjModelLoader.get(def.model().get()).orElse(null);
		if (model == null) {
			if (REPORTED_MISSING.add(def.model().get())) {
				TudursGuns.LOGGER.warn("Handheld weapon '{}' model {} was not found", weaponId, def.model().get());
			}
			return;
		}

		matrices.push();
		HandheldDefinition.DisplayTransform transform =
				def.display().getOrDefault(displayContext, HandheldDefinition.DisplayTransform.IDENTITY);
		matrices.translate(transform.translation().x(), transform.translation().y(), transform.translation().z());
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(transform.rotation().x()));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(transform.rotation().y()));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(transform.rotation().z()));
		matrices.scale(transform.scale().x(), transform.scale().y(), transform.scale().z());
		VehicleEntityRenderer.renderTriangles(queue, matrices, DitherCutoutLayers.entityDitherCutout(def.texture().get()),
				model.getTriangles(), light, overlay, 0xFFFFFFFF);
		matrices.pop();
	}

	/** Extents used for item bounds - the unit cube the item model space is built around. */
	@Override
	public void collectVertices(Consumer<Vector3f> consumer) {
		for (int x = 0; x <= 1; x++) {
			for (int y = 0; y <= 1; y++) {
				for (int z = 0; z <= 1; z++) {
					consumer.accept(new Vector3f(x, y, z));
				}
			}
		}
	}

	public record Unbaked() implements SpecialModelRenderer.Unbaked {

		public static final MapCodec<Unbaked> CODEC = MapCodec.unit(new Unbaked());

		@Override
		public MapCodec<Unbaked> getCodec() {
			return CODEC;
		}

		@Override
		public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakeContext context) {
			return new ObjHandheldModelRenderer();
		}
	}
}
