package com.example.tudursguns.client.render;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.client.AimController;
import com.example.tudursguns.client.TudursGunsClientConfig;
import com.example.tudursguns.client.mixin.GameRendererAccessor;
import com.example.tudursguns.client.mixin.HeldItemRendererInvoker;
import com.example.tudursguns.handheld.AnimationDefinition;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.weapon.WeaponAnimationEvents;
import com.example.tudursguns.weapon.WeaponModifiers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Draws a handheld weapon (with its fitted attachments) as its item model.
 *
 * Everywhere except the local player's first-person main hand, the item model JSON supplies the base
 * display transform and the definition's "display" entry for that context is applied on top.
 *
 * In first person, a weapon whose definition has an "aim" section is instead placed directly in
 * camera space, ignoring vanilla's held-item placement: lowered at the hip pose, or raised so its
 * sight lands exactly on the screen centre, blending between the two as the player aims. The
 * player's arms are drawn holding it. Looking through a scope hides it entirely (the scope overlay
 * takes over). */
public class ObjHandheldModelRenderer implements SpecialModelRenderer<ObjHandheldModelRenderer.Data> {

	public static final Identifier TYPE_ID = Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, "obj_handheld");

	/** What the renderer needs from the stack. raised / sprintCarry: the entity holding it is in the
	 * aiming / sprint pose (third-person contexts only - see AimRenderState.entityBeingUpdatedAims). */
	public record Data(Identifier weaponId, Map<String, Identifier> fitted, boolean raised, boolean sprintCarry,
			Map<String, WeaponAnimationEvents.Occurrence> events, Map<String, Integer> counters, int ammo) {
	}

	@Override
	public Data extractArgument(ItemStack stack) {
		Identifier weaponId = stack.get(ModComponents.WEAPON);
		return weaponId == null ? null
				: new Data(weaponId, WeaponModifiers.fitted(stack), AimRenderState.entityBeingUpdatedAims(),
						AimRenderState.entityBeingUpdatedSprintCarries(),
						stack.getOrDefault(ModComponents.ANIM_EVENTS, Map.of()), stack.getOrDefault(ModComponents.ANIM_COUNTERS, Map.of()),
						stack.getOrDefault(ModComponents.AMMO, 0));
	}

	@Override
	public void submit(Data data, ItemDisplayContext displayContext, PoseStack matrices,
			SubmitNodeCollector queue, int light, int overlay, boolean glint, int outlineColor) {
		if (data == null) {
			return;
		}
		HandheldDefinition def = ModDefinitions.HANDHELD.getAny(data.weaponId());
		if (def == null) {
			return;
		}
		if (displayContext == ItemDisplayContext.GUI && def.handling().icon().isPresent()) {
			WeaponModelDrawer.drawIcon(queue, matrices, def.handling().icon().get(), light, overlay);
			return;
		}
		boolean firstPerson = def.aim().isPresent() && isLocalMainHand(displayContext);
		float tickProgress = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		// How far the weapon is raised: the local player's own smooth progress in first person, the
		// holder's aiming pose (all or nothing) otherwise.
		float aimProgress = firstPerson
				? AimController.progress(tickProgress)
				: (data.raised() && isThirdPersonHand(displayContext) ? 1f : 0f);
		WeaponPose pose = pose(def, data, isHand(displayContext), aimProgress, tickProgress);
		if (firstPerson) {
			renderFirstPerson(def, data.fitted(), pose, aimProgress, tickProgress, matrices, queue, light, overlay);
			return;
		}
		HandheldDefinition.DisplayTransform transform =
				def.display().getOrDefault(displayContext, HandheldDefinition.DisplayTransform.IDENTITY);
		// third_person_aiming / _sprinting replace the display transform in those poses, when set.
		if (data.raised() && isThirdPersonHand(displayContext)) {
			transform = def.aim().flatMap(HandheldDefinition.AimSettings::thirdPersonAiming).orElse(transform);
		} else if (data.sprintCarry() && isThirdPersonHand(displayContext)) {
			transform = def.aim().flatMap(HandheldDefinition.AimSettings::thirdPersonSprinting).orElse(transform);
		}
		matrices.pushPose();
		WeaponModelDrawer.applyTransform(matrices, transform);
		if (pose != null) {
			pose.applyRoot(matrices);
		}
		WeaponModelDrawer.drawWeapon(queue, matrices, def, data.fitted(), light, overlay, pose);
		matrices.popPose();
	}

	/** The definition's animated pose right now, or null if it has no animation. Motions (sequences)
	 * only play in hand; elsewhere just the counters' resting positions show. */
	private static WeaponPose pose(HandheldDefinition def, Data data, boolean inHand, float aimProgress, float tickProgress) {
		if (def.animation().isEmpty()) {
			return null;
		}
		Minecraft client = Minecraft.getInstance();
		double now = client.level == null ? 0.0 : client.level.getGameTime() + tickProgress;
		return WeaponPose.compute(def.animation().get(), data.events(), data.counters(), now, inHand && client.level != null,
				aimProgress, data.ammo());
	}

	private static boolean isHand(ItemDisplayContext displayContext) {
		return isThirdPersonHand(displayContext) || displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
				|| displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
	}

	private static boolean isThirdPersonHand(ItemDisplayContext displayContext) {
		return displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
	}

	/** First-person contexts are only ever the local player's; the main hand is the right one unless
	 * the player is left-handed. */
	private static boolean isLocalMainHand(ItemDisplayContext displayContext) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return false;
		}
		boolean leftHanded = client.player.getMainArm() == HumanoidArm.LEFT;
		return displayContext == (leftHanded ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
	}

	/** A first-person hand pose (camera space; rotation in degrees) plus the config's hip offset,
	 * mirrored for the left hand. Writes the translation, returns the rotation. */
	private static Quaternionf handPose(Vector3fc translation, Vector3fc rotation, boolean leftHanded, Vector3f translationOut) {
		translationOut.set(translation).add(TudursGunsClientConfig.hipOffset());
		float mirror = leftHanded ? -1f : 1f;
		translationOut.x *= mirror;
		return new Quaternionf().rotationXYZ((float) Math.toRadians(rotation.x()),
				(float) Math.toRadians(rotation.y() * mirror), (float) Math.toRadians(rotation.z() * mirror));
	}

	private static void renderFirstPerson(HandheldDefinition def, Map<String, Identifier> fitted, WeaponPose pose, float aimProgress,
			float tickProgress, PoseStack matrices, SubmitNodeCollector queue, int light, int overlay) {
		if (AimController.isScoped()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		HandheldDefinition.AimSettings aim = def.aim().get();
		float scale = aim.scale();
		boolean leftHanded = client.player.getMainArm() == HumanoidArm.LEFT;

		float eased = AnimationDefinition.Easing.SMOOTH.apply(aimProgress);

		// Hip: the definition's pose. Sprinting swings the weapon across the body, blended in from the
		// hip pose; aiming always wins, as it ends the sprint.
		Vector3f hipTranslation = new Vector3f();
		Quaternionf hipQuaternion = handPose(aim.hipTranslation(), aim.hipRotation(), leftHanded, hipTranslation);
		float sprint = AnimationDefinition.Easing.SMOOTH.apply(AimController.sprintProgress(tickProgress));
		if (sprint > 0f) {
			Vector3f sprintTranslation = new Vector3f();
			Quaternionf sprintQuaternion = handPose(aim.sprintTranslation(), aim.sprintRotation(), leftHanded, sprintTranslation);
			hipTranslation.lerp(sprintTranslation, sprint);
			hipQuaternion.slerp(sprintQuaternion, sprint);
		}

		// Aiming: no rotation (the model's barrel already points down -Z, the view direction), placed
		// so the sight is eye_distance straight ahead of the eye - i.e. on the screen centre.
		WeaponModifiers modifiers = WeaponModifiers.of(client.player.getMainHandItem(), def);
		Vector3fc sight = modifiers.sightOverride() != null ? modifiers.sightOverride() : aim.sightPosition();
		Vector3f aimTranslation = new Vector3f(0f, 0f, -aim.eyeDistance())
				.add(TudursGunsClientConfig.aimOffset())
				.sub(sight.x() * scale, sight.y() * scale, sight.z() * scale);

		Vector3f translation = hipTranslation.lerp(aimTranslation, eased, new Vector3f());
		Quaternionf rotation = hipQuaternion.slerp(new Quaternionf(), eased, new Quaternionf());

		matrices.pushPose();
		// Start from the camera-relative frame first-person rendering began with (it follows the view
		// and carries view bobbing), dropping vanilla's own hand placement and sway.
		FirstPersonBase.apply(matrices);
		matrices.translate(translation.x, translation.y, translation.z);
		matrices.mulPose(rotation);
		matrices.scale(scale, scale, scale);
		// The whole weapon's own motion (a tilt for reloading, recoil) - the arms move with it.
		if (pose != null) {
			pose.applyRoot(matrices);
		}

		WeaponModelDrawer.drawWeapon(queue, matrices, def, fitted, light, overlay, pose);
		if (TudursGunsClientConfig.showArms()) {
			HeldItemRendererInvoker arms = (HeldItemRendererInvoker) ((GameRendererAccessor) client.gameRenderer).tudursguns$getFirstPersonRenderer();
			aim.rightArm().ifPresent(transform -> drawArm(arms, matrices, queue, light, transform,
					TudursGunsClientConfig.rightArmOffset(), leftHanded ? HumanoidArm.LEFT : HumanoidArm.RIGHT, pose, AnimationDefinition.RIGHT_ARM));
			aim.leftArm().ifPresent(transform -> drawArm(arms, matrices, queue, light, transform,
					TudursGunsClientConfig.leftArmOffset(), leftHanded ? HumanoidArm.RIGHT : HumanoidArm.LEFT, pose, AnimationDefinition.LEFT_ARM));
		}
		matrices.popPose();
	}

	private static void drawArm(HeldItemRendererInvoker arms, PoseStack matrices, SubmitNodeCollector queue, int light,
			HandheldDefinition.DisplayTransform transform, Vector3f offset, HumanoidArm arm, WeaponPose pose, String part) {
		matrices.pushPose();
		if (pose != null) {
			pose.applyChain(matrices, part);
		}
		matrices.translate(offset.x, offset.y, offset.z);
		WeaponModelDrawer.applyTransform(matrices, transform);
		arms.tudursguns$renderArm(matrices, queue, light, arm);
		matrices.popPose();
	}

	@Override
	public void getExtents(Consumer<Vector3fc> consumer) {
		WeaponModelDrawer.unitCube(consumer);
	}

	public record Unbaked() implements SpecialModelRenderer.Unbaked {

		public static final MapCodec<com.example.tudursguns.client.render.ObjHandheldModelRenderer.Unbaked> CODEC = MapCodec.unit(new com.example.tudursguns.client.render.ObjHandheldModelRenderer.Unbaked());

		@Override
		public MapCodec<com.example.tudursguns.client.render.ObjHandheldModelRenderer.Unbaked> type() {
			return CODEC;
		}

		@Override
		public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext context) {
			return new ObjHandheldModelRenderer();
		}
	}
}
