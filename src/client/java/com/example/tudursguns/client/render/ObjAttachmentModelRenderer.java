package com.example.tudursguns.client.render;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.handheld.AttachmentDefinition;
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

/** Draws an attachment item from its definition's own model/texture/display. */
public class ObjAttachmentModelRenderer implements SpecialModelRenderer<Identifier> {

	public static final Identifier TYPE_ID = Identifier.of(TudursGuns.MOD_ID, "obj_attachment");

	@Override
	public Identifier getData(ItemStack stack) {
		return stack.get(ModComponents.ATTACHMENT);
	}

	@Override
	public void render(Identifier attachmentId, ItemDisplayContext displayContext, MatrixStack matrices,
			OrderedRenderCommandQueue queue, int light, int overlay, boolean glint, int outlineColor) {
		AttachmentDefinition def = HandheldDefinitions.getAnyAttachment(attachmentId);
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
			return new ObjAttachmentModelRenderer();
		}
	}
}
