package com.example.tudursguns.client.render;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.handheld.AmmoDefinition;
import com.example.tudursguns.handheld.ArmorDefinition;
import com.example.tudursguns.handheld.AttachmentDefinition;
import com.example.tudursguns.handheld.EquipmentDefinition;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.MineDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.handheld.ThrowableDefinition;
import com.example.tudursguns.registry.ModComponents;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3fc;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Draws a data-defined item (one item model type per Kind: tudursguns:obj_attachment, obj_mine, ...)
 * from its definition's OBJ model, texture and display transforms, or its flat icon.
 *
 * OBJ armor is modelled facing +Z everywhere. In the "head" context (an OBJ-only helmet, drawn by
 * vanilla on the head like a carved pumpkin) vanilla's front is -Z, so the model is turned 180
 * degrees about the item's centre before display.head is applied. */
public class ObjDefinedItemRenderer implements SpecialModelRenderer<Identifier> {

	/** What a definition says about its item model. */
	private record Look(Optional<Identifier> model, Optional<Identifier> texture,
			Map<ItemDisplayContext, HandheldDefinition.DisplayTransform> display, Optional<Identifier> icon) {

		Look(Optional<Identifier> model, Optional<Identifier> texture, Map<ItemDisplayContext, HandheldDefinition.DisplayTransform> display) {
			this(model, texture, display, Optional.empty());
		}
	}

	public enum Kind {
		ATTACHMENT("obj_attachment"),
		THROWABLE("obj_throwable"),
		MINE("obj_mine"),
		ARMOR("obj_armor"),
		EQUIPMENT("obj_equipment"),
		AMMO("obj_ammo");

		public final Identifier typeId;
		public final MapCodec<Unbaked> codec;

		Kind(String path) {
			this.typeId = Identifier.of(TudursGuns.MOD_ID, path);
			this.codec = MapCodec.unit(new Unbaked(this));
		}

		Identifier idOf(ItemStack stack) {
			return switch (this) {
				case ATTACHMENT -> stack.get(ModComponents.ATTACHMENT);
				case THROWABLE -> stack.get(ModComponents.THROWABLE);
				case MINE -> stack.get(ModComponents.MINE);
				case ARMOR -> stack.get(ModComponents.ARMOR);
				case EQUIPMENT -> stack.get(ModComponents.EQUIPMENT);
				case AMMO -> stack.get(ModComponents.AMMO_TYPE);
			};
		}

		Look look(Identifier id) {
			return switch (this) {
				case ATTACHMENT -> {
					AttachmentDefinition def = ModDefinitions.ATTACHMENTS.getAny(id);
					yield def == null ? null : new Look(def.model(), def.texture(), def.display());
				}
				case THROWABLE -> {
					ThrowableDefinition def = ModDefinitions.THROWABLES.getAny(id);
					yield def == null ? null : new Look(def.model(), def.texture(), def.display());
				}
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
				case AMMO -> {
					AmmoDefinition def = ModDefinitions.AMMO.getAny(id);
					yield def == null ? null : new Look(def.model(), def.texture(), def.display(), def.icon());
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
		if (look == null) {
			return;
		}
		// A flat icon in inventories - or everywhere, when there's no model.
		boolean noModel = look.model().isEmpty() || look.texture().isEmpty();
		if (look.icon().isPresent() && (displayContext == ItemDisplayContext.GUI || noModel)) {
			matrices.push();
			if (displayContext != ItemDisplayContext.GUI) {
				WeaponModelDrawer.applyTransform(matrices, look.display().getOrDefault(displayContext, HandheldDefinition.DisplayTransform.IDENTITY));
			}
			WeaponModelDrawer.drawIcon(queue, matrices, look.icon().get(), light, overlay);
			matrices.pop();
			return;
		}
		if (noModel) {
			return;
		}
		matrices.push();
		if (this.kind == Kind.ARMOR && displayContext == ItemDisplayContext.HEAD) {
			// Vanilla draws a head-slot item with its front towards -Z (like a carved pumpkin's face);
			// OBJ armor is modelled with +Z forward (as for "worn"), so turn it round the item's centre.
			matrices.translate(0.5, 0.0, 0.5);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
			matrices.translate(-0.5, 0.0, -0.5);
		}
		WeaponModelDrawer.applyTransform(matrices, look.display().getOrDefault(displayContext, HandheldDefinition.DisplayTransform.IDENTITY));
		WeaponModelDrawer.drawObj(queue, matrices, look.model().get(), look.texture().get(), Set.of(), light, overlay);
		matrices.pop();
	}

	@Override
	public void collectVertices(Consumer<Vector3fc> consumer) {
		WeaponModelDrawer.unitCube(consumer);
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
