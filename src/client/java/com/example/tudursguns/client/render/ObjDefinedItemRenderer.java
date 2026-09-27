package com.example.tudursguns.client.render;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.handheld.ArmorDefinition;
import com.example.tudursguns.handheld.EquipmentDefinition;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.MineDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
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

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Draws a mine, armor or equipment item from its definition's own OBJ model/texture/display (one
 * item model type per kind: tudursguns:obj_mine, obj_armor, obj_equipment). */
public class ObjDefinedItemRenderer implements SpecialModelRenderer<Identifier> {

	/** What a definition says about its item model. */
	private record Look(Optional<Identifier> model, Optional<Identifier> texture,
			Map<ItemDisplayContext, HandheldDefinition.DisplayTransform> display) {
	}

	public enum Kind {
		MINE("obj_mine"),
		ARMOR("obj_armor"),
		EQUIPMENT("obj_equipment");

		public final Identifier typeId;
		public final MapCodec<Unbaked> codec;

		Kind(String path) {
			this.typeId = Identifier.of(TudursGuns.MOD_ID, path);
			this.codec = MapCodec.unit(new Unbaked(this));
		}

		Identifier idOf(ItemStack stack) {
			return switch (this) {
				case MINE -> stack.get(ModComponents.MINE);
				case ARMOR -> stack.get(ModComponents.ARMOR);
				case EQUIPMENT -> stack.get(ModComponents.EQUIPMENT);
			};
		}

		Look look(Identifier id) {
			return switch (this) {
				case MINE -> {
					MineDefinition def = ModDefinitions.MINES.getAny(id);
					yield def == null ? null : new Look(def.model(), def.texture(), def.display());
				}
				case ARMOR -> {
					ArmorDefinition def = ModDefinitions.ARMOR.getAny(id);
					yield def == null ? null : new Look(def.model(), def.texture(), def.display());
				}
				case EQUIPMENT -> {
					EquipmentDefinition def = ModDefinitions.EQUIPMENT.getAny(id);
					yield def == null ? null : new Look(def.model(), def.texture(), def.display());
				}
			};
		}
	}

	private final Kind kind;

	public ObjDefinedItemRenderer(Kind kind) {
		this.kind = kind;
	}

	@Override
	public Identifier getData(ItemStack stack) {
		return this.kind.idOf(stack);
	}

	@Override
	public void render(Identifier id, ItemDisplayContext displayContext, MatrixStack matrices,
			OrderedRenderCommandQueue queue, int light, int overlay, boolean glint, int outlineColor) {
		Look look = id == null ? null : this.kind.look(id);
		if (look == null || look.model().isEmpty() || look.texture().isEmpty()) {
			return;
		}
		matrices.push();
		WeaponModelDrawer.applyTransform(matrices, look.display().getOrDefault(displayContext, HandheldDefinition.DisplayTransform.IDENTITY));
		WeaponModelDrawer.drawObj(queue, matrices, look.model().get(), look.texture().get(), Set.of(), light, overlay);
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

	public record Unbaked(Kind kind) implements SpecialModelRenderer.Unbaked {

		@Override
		public MapCodec<Unbaked> getCodec() {
			return this.kind.codec;
		}

		@Override
		public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakeContext context) {
			return new ObjDefinedItemRenderer(this.kind);
		}
	}
}
