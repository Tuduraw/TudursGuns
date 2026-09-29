package com.example.tudursguns.client.render;

import com.example.tudursguns.handheld.ArmorDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.item.ArmorItem;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursvehiclemod.client.render.ObjModel;
import com.example.tudursvehiclemod.client.render.ObjModelLoader;
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

import java.util.List;
import java.util.Set;

/** Draws OBJ armor (definitions with a model) on anything with a humanoid model - players, armor
 * stands, zombies, soldiers...
 *
 * Each piece is drawn in the space of a body part: origin at the part's pivot (neck for the head and
 * body, shoulder for an arm, hip for a leg), +Y up, +Z the way the wearer faces, -X the wearer's
 * right, 1 = a block (a skin pixel is 1/16) - then moved by the definition's "worn" transform.
 * - A model with groups named after body parts (head, body, right_arm, left_arm, right_leg,
 *   left_leg) has each of those groups drawn on its part, so it moves with it; other groups aren't drawn.
 * - Any other model is drawn whole on its slot's part: the head, the body, or each leg (legs, feet).
 *
 * Vanilla draws nothing for these: ArmorEffects gives them an empty equipment asset, which also
 * stops a helmet being drawn as a block-like item on the head. */
public class ObjArmorFeatureRenderer<S extends BipedEntityRenderState, M extends BipedEntityModel<S>> extends FeatureRenderer<S, M> {

	private static final List<String> PART_GROUPS = List.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg");

	public ObjArmorFeatureRenderer(FeatureRendererContext<S, M> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, S state, float limbAngle, float limbDistance) {
		M model = this.getContextModel();
		draw(matrices, queue, light, model, state.equippedHeadStack, EquipmentSlot.HEAD);
		draw(matrices, queue, light, model, state.equippedChestStack, EquipmentSlot.CHEST);
		draw(matrices, queue, light, model, state.equippedLegsStack, EquipmentSlot.LEGS);
		draw(matrices, queue, light, model, state.equippedFeetStack, EquipmentSlot.FEET);
	}

	private static void draw(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, BipedEntityModel<?> model, ItemStack stack,
			EquipmentSlot slot) {
		if (stack == null || !(stack.getItem() instanceof ArmorItem)) {
			return;
		}
		ArmorDefinition def = ModDefinitions.ARMOR.getAny(stack.get(ModComponents.ARMOR));
		if (def == null || def.slot() != slot || def.model().isEmpty() || def.texture().isEmpty()) {
			return;
		}
		ObjModel obj = ObjModelLoader.get(def.model().get()).orElse(null);
		Set<String> groups = obj != null ? obj.getGroupNames() : Set.of();
		boolean split = PART_GROUPS.stream().anyMatch(groups::contains);
		if (split) {
			for (String group : PART_GROUPS) {
				if (groups.contains(group)) {
					drawOn(matrices, queue, light, def, part(model, group), group);
				}
			}
			return;
		}
		switch (slot) {
			case HEAD -> drawOn(matrices, queue, light, def, model.head, null);
			case CHEST -> drawOn(matrices, queue, light, def, model.body, null);
			default -> {
				drawOn(matrices, queue, light, def, model.rightLeg, null);
				drawOn(matrices, queue, light, def, model.leftLeg, null);
			}
		}
	}

	private static ModelPart part(BipedEntityModel<?> model, String group) {
		return switch (group) {
			case "head" -> model.head;
			case "body" -> model.body;
			case "right_arm" -> model.rightArm;
			case "left_arm" -> model.leftArm;
			case "right_leg" -> model.rightLeg;
			default -> model.leftLeg;
		};
	}

	/** The whole model (group null) or one group, in part's space. */
	private static void drawOn(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, ArmorDefinition def, ModelPart part, String group) {
		if (!part.visible) {
			return;
		}
		matrices.push();
		part.applyTransform(matrices);
		// Model space is upside down and facing -Z; turn it to +Y up, +Z forward.
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180f));
		WeaponModelDrawer.applyTransform(matrices, def.worn());
		if (group == null) {
			WeaponModelDrawer.drawObj(queue, matrices, def.model().get(), def.texture().get(), Set.of(), light, OverlayTexture.DEFAULT_UV);
		} else {
			WeaponModelDrawer.drawGroups(queue, matrices, def.model().get(), def.texture().get(), List.of(group), Set.of(), light,
					OverlayTexture.DEFAULT_UV);
		}
		matrices.pop();
	}
}
