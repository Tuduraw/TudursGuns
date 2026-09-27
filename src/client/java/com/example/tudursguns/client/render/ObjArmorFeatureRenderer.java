package com.example.tudursguns.client.render;

import com.example.tudursguns.handheld.ArmorDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.item.ArmorItem;
import com.example.tudursguns.registry.ModComponents;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;

import java.util.Set;

/** Draws OBJ armor (definitions with a model) on anything with a humanoid model - players, armor
 * stands, zombies... Each piece follows the model part of its slot: head, body, or both legs (legs
 * and feet). The definition's "worn" transform places it in that part's space: origin at the part's
 * pivot (neck for the head and body, hip for a leg), +Y up, +Z the way the wearer faces, 1 = a block.
 * 2D (equipment asset) armor is drawn by vanilla as usual.
 *
 * A helmet without an equipment asset is left to vanilla too: like a carved pumpkin, vanilla draws a
 * head-slot item with no equipment asset as its item model in the "head" display context - so an OBJ
 * helmet is placed by its definition's display.head. Only a helmet that has both (2D layer plus an
 * OBJ part, e.g. goggles) is drawn here, using worn. */
public class ObjArmorFeatureRenderer<S extends BipedEntityRenderState, M extends BipedEntityModel<S>> extends FeatureRenderer<S, M> {

	public ObjArmorFeatureRenderer(FeatureRendererContext<S, M> context) {
		super(context);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static FeatureRenderer create(FeatureRendererContext context) {
		return new ObjArmorFeatureRenderer(context);
	}

	@Override
	public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, S state, float limbAngle, float limbDistance) {
		M model = this.getContextModel();
		draw(matrices, queue, light, state.equippedHeadStack, EquipmentSlot.HEAD, model.head);
		draw(matrices, queue, light, state.equippedChestStack, EquipmentSlot.CHEST, model.body);
		draw(matrices, queue, light, state.equippedLegsStack, EquipmentSlot.LEGS, model.rightLeg);
		draw(matrices, queue, light, state.equippedLegsStack, EquipmentSlot.LEGS, model.leftLeg);
		draw(matrices, queue, light, state.equippedFeetStack, EquipmentSlot.FEET, model.rightLeg);
		draw(matrices, queue, light, state.equippedFeetStack, EquipmentSlot.FEET, model.leftLeg);
	}

	private static void draw(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, ItemStack stack, EquipmentSlot slot, ModelPart part) {
		if (stack == null || !(stack.getItem() instanceof ArmorItem)) {
			return;
		}
		ArmorDefinition def = ModDefinitions.ARMOR.getAny(stack.get(ModComponents.ARMOR));
		if (def == null || def.slot() != slot || def.model().isEmpty() || def.texture().isEmpty() || !part.visible) {
			return;
		}
		if (slot == EquipmentSlot.HEAD && def.equipmentAsset().isEmpty()) {
			return;
		}
		matrices.push();
		part.applyTransform(matrices);
		// Model space is upside down and facing -Z; turn it to +Y up, +Z forward.
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180f));
		WeaponModelDrawer.applyTransform(matrices, def.worn());
		WeaponModelDrawer.drawObj(queue, matrices, def.model().get(), def.texture().get(), Set.of(), light, OverlayTexture.DEFAULT_UV);
		matrices.pop();
	}
}
