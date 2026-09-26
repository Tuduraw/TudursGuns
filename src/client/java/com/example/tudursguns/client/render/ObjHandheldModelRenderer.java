package com.example.tudursguns.client.render;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.client.AimController;
import com.example.tudursguns.client.TudursGunsClientConfig;
import com.example.tudursguns.client.mixin.GameRendererAccessor;
import com.example.tudursguns.client.mixin.HeldItemRendererInvoker;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.HandheldDefinitions;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.weapon.WeaponModifiers;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Map;
import java.util.function.Consumer;

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

	public static final Identifier TYPE_ID = Identifier.of(TudursGuns.MOD_ID, "obj_handheld");

	/** What the renderer needs from the stack. raised: the entity holding it is in the aiming pose
	 * (only meaningful for third-person contexts - see AimRenderState.ENTITY_BEING_UPDATED_AIMS). */
	public record Data(Identifier weaponId, Map<String, Identifier> fitted, boolean raised) {
	}

	@Override
	public Data getData(ItemStack stack) {
		Identifier weaponId = stack.get(ModComponents.WEAPON);
		return weaponId == null ? null
				: new Data(weaponId, WeaponModifiers.fitted(stack), AimRenderState.entityBeingUpdatedAims());
	}

	@Override
	public void render(Data data, ItemDisplayContext displayContext, MatrixStack matrices,
			OrderedRenderCommandQueue queue, int light, int overlay, boolean glint, int outlineColor) {
		if (data == null) {
			return;
		}
		HandheldDefinitions.ClientEntry entry = HandheldDefinitions.getClient(data.weaponId());
		if (entry == null) {
			return;
		}
		HandheldDefinition def = entry.definition();
		if (def.aim().isPresent() && isLocalMainHand(displayContext)) {
			renderFirstPerson(def, data.fitted(), matrices, queue, light, overlay);
			return;
		}
		HandheldDefinition.DisplayTransform transform =
				def.display().getOrDefault(displayContext, HandheldDefinition.DisplayTransform.IDENTITY);
		// The raised pose normally needs no correction (tested in game: the weapon stays level as the
		// arm comes up); third_person_aiming is there for a model that does need one.
		if (data.raised() && isThirdPersonHand(displayContext)) {
			transform = def.aim().flatMap(HandheldDefinition.AimSettings::thirdPersonAiming).orElse(transform);
		}
		matrices.push();
		WeaponModelDrawer.applyTransform(matrices, transform);
		WeaponModelDrawer.drawWeapon(queue, matrices, def, data.fitted(), light, overlay);
		matrices.pop();
	}

	private static boolean isThirdPersonHand(ItemDisplayContext displayContext) {
		return displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
	}

	/** First-person contexts are only ever the local player's; the main hand is the right one unless
	 * the player is left-handed. */
	private static boolean isLocalMainHand(ItemDisplayContext displayContext) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return false;
		}
		boolean leftHanded = client.player.getMainArm() == Arm.LEFT;
		return displayContext == (leftHanded ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
	}

	private static void renderFirstPerson(HandheldDefinition def, Map<String, Identifier> fitted, MatrixStack matrices,
			OrderedRenderCommandQueue queue, int light, int overlay) {
		if (AimController.isScoped()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		HandheldDefinition.AimSettings aim = def.aim().get();
		float scale = aim.scale();
		boolean leftHanded = client.player.getMainArm() == Arm.LEFT;

		float t = AimController.progress(client.getRenderTickCounter().getTickProgress(true));
		float eased = t * t * (3f - 2f * t);

		// Hip: the definition's pose (mirrored for the left hand) plus the config offset.
		Vector3f hipTranslation = new Vector3f(aim.hipTranslation()).add(TudursGunsClientConfig.hipOffset());
		Vector3f hipRotation = new Vector3f(aim.hipRotation());
		if (leftHanded) {
			hipTranslation.x = -hipTranslation.x;
			hipRotation.y = -hipRotation.y;
			hipRotation.z = -hipRotation.z;
		}
		Quaternionf hipQuaternion = new Quaternionf().rotationXYZ(
				(float) Math.toRadians(hipRotation.x), (float) Math.toRadians(hipRotation.y), (float) Math.toRadians(hipRotation.z));

		// Aiming: no rotation (the model's barrel already points down -Z, the view direction), placed
		// so the sight is eye_distance straight ahead of the eye - i.e. on the screen centre.
		WeaponModifiers modifiers = WeaponModifiers.of(client.player.getMainHandStack(), def);
		Vector3fc sight = modifiers.sightOverride() != null ? modifiers.sightOverride() : aim.sightPosition();
		Vector3f aimTranslation = new Vector3f(0f, 0f, -aim.eyeDistance())
				.add(TudursGunsClientConfig.aimOffset())
				.sub(sight.x() * scale, sight.y() * scale, sight.z() * scale);

		Vector3f translation = hipTranslation.lerp(aimTranslation, eased, new Vector3f());
		Quaternionf rotation = hipQuaternion.slerp(new Quaternionf(), eased, new Quaternionf());

		matrices.push();
		// Start from the camera-relative frame first-person rendering began with (it follows the view
		// and carries view bobbing), dropping vanilla's own hand placement and sway.
		FirstPersonBase.apply(matrices);
		matrices.translate(translation.x, translation.y, translation.z);
		matrices.multiply(rotation);
		matrices.scale(scale, scale, scale);

		WeaponModelDrawer.drawWeapon(queue, matrices, def, fitted, light, overlay);
		if (TudursGunsClientConfig.showArms()) {
			HeldItemRendererInvoker arms = (HeldItemRendererInvoker) ((GameRendererAccessor) client.gameRenderer).tudursguns$getFirstPersonRenderer();
			aim.rightArm().ifPresent(transform -> drawArm(arms, matrices, queue, light, transform,
					TudursGunsClientConfig.rightArmOffset(), leftHanded ? Arm.LEFT : Arm.RIGHT));
			aim.leftArm().ifPresent(transform -> drawArm(arms, matrices, queue, light, transform,
					TudursGunsClientConfig.leftArmOffset(), leftHanded ? Arm.RIGHT : Arm.LEFT));
		}
		matrices.pop();
	}

	private static void drawArm(HeldItemRendererInvoker arms, MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
			HandheldDefinition.DisplayTransform transform, Vector3f offset, Arm arm) {
		matrices.push();
		matrices.translate(offset.x, offset.y, offset.z);
		WeaponModelDrawer.applyTransform(matrices, transform);
		arms.tudursguns$renderArm(matrices, queue, light, arm);
		matrices.pop();
	}

	/** Extents used for item bounds - the unit cube the item model space is built around. */
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
			return new ObjHandheldModelRenderer();
		}
	}
}
