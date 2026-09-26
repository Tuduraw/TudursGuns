package com.example.tudursguns.client.render;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.handheld.ThrowableDefinition;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.HandheldDefinitions;
import com.example.tudursguns.registry.ModComponents;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Set;
import java.util.function.Consumer;

/** Draws a throwable item (in hand, in inventories and in flight) from its definition's own model/texture/display. */
public class ObjThrowableModelRenderer implements SpecialModelRenderer<Identifier> {

	public static final Identifier TYPE_ID = Identifier.of(TudursGuns.MOD_ID, "obj_throwable");

	@Override
	public Identifier getData(ItemStack stack) {
		return stack.get(ModComponents.THROWABLE);
	}

	@Override
	public void render(Identifier throwableId, ItemDisplayContext displayContext, MatrixStack matrices,
			OrderedRenderCommandQueue queue, int light, int overlay, boolean glint, int outlineColor) {
		ThrowableDefinition def = HandheldDefinitions.getAnyThrowable(throwableId);
		if (def == null || def.model().isEmpty() || def.texture().isEmpty()) {
			return;
		}
		matrices.push();
		WeaponModelDrawer.applyTransform(matrices,
				def.display().getOrDefault(displayContext, HandheldDefinition.DisplayTransform.IDENTITY));
		WeaponModelDrawer.drawObj(queue, matrices, def.model().get(), def.texture().get(), Set.of(), light, overlay);
		matrices.pop();
	}

	@Override
	public void collectVertices(Consumer<Vector3fc> consumer) {
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
			return new ObjThrowableModelRenderer();
		}
	}
}
