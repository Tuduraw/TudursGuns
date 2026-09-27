package com.example.tudursguns.handheld;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** One handheld weapon, read from data/<namespace>/handheld/<name>.json.
 *
 * Ballistics, ammunition capacity, reload time, rate of fire, guidance and sound all come from the
 * weapon file this points at (weapon = the .txt name, same as a vehicle JSON's weapon_name) and are
 * read live through Tudur's Vehicle Mod's own WeaponStatsLoader. This file only holds what is
 * specific to carrying the weapon by hand. */
public record HandheldDefinition(
		String weapon,
		Optional<String> displayName,
		Optional<Identifier> model,
		Optional<Identifier> texture,
		Identifier projectileItem,
		Optional<Identifier> ammoItem,
		int roundsPerAmmoItem,
		FireMode fireMode,
		Vector3f muzzleOffset,
		boolean inheritShooterVelocity,
		Presentation presentation,
		Map<ItemDisplayContext, DisplayTransform> display,
		Optional<AimSettings> aim,
		Map<String, AttachmentSlot> attachments,
		HeldMovement movement,
		Optional<AnimationDefinition> animation
) {

	/** [x, y, z] as a JSON array of three numbers. */
	public static final Codec<Vector3f> VECTOR_3F = Codec.FLOAT.listOf().comapFlatMap(
			list -> Util.decodeFixedLengthList(list, 3).map(xyz -> new Vector3f(xyz.get(0), xyz.get(1), xyz.get(2))),
			vector -> List.of(vector.x(), vector.y(), vector.z()));

	public static final Codec<HandheldDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("weapon").forGetter(HandheldDefinition::weapon),
			Codec.STRING.optionalFieldOf("display_name").forGetter(HandheldDefinition::displayName),
			Identifier.CODEC.optionalFieldOf("model").forGetter(HandheldDefinition::model),
			Identifier.CODEC.optionalFieldOf("texture").forGetter(HandheldDefinition::texture),
			Identifier.CODEC.optionalFieldOf("projectile_item", Identifier.ofVanilla("iron_nugget")).forGetter(HandheldDefinition::projectileItem),
			Identifier.CODEC.optionalFieldOf("ammo_item").forGetter(HandheldDefinition::ammoItem),
			Codec.intRange(1, 1_000_000).optionalFieldOf("rounds_per_ammo_item", 1).forGetter(HandheldDefinition::roundsPerAmmoItem),
			FireMode.CODEC.optionalFieldOf("fire_mode", FireMode.SEMI).forGetter(HandheldDefinition::fireMode),
			VECTOR_3F.optionalFieldOf("muzzle_offset", new Vector3f(0.25f, -0.2f, 0.8f)).forGetter(HandheldDefinition::muzzleOffset),
			Codec.BOOL.optionalFieldOf("inherit_shooter_velocity", false).forGetter(HandheldDefinition::inheritShooterVelocity),
			Presentation.MAP_CODEC.forGetter(HandheldDefinition::presentation),
			Codec.unboundedMap(ItemDisplayContext.CODEC, DisplayTransform.CODEC).optionalFieldOf("display", Map.of()).forGetter(HandheldDefinition::display),
			AimSettings.CODEC.optionalFieldOf("aim").forGetter(HandheldDefinition::aim),
			Codec.unboundedMap(Codec.STRING, AttachmentSlot.CODEC).optionalFieldOf("attachments", Map.of()).forGetter(HandheldDefinition::attachments),
			HeldMovement.MAP_CODEC.forGetter(HandheldDefinition::movement),
			AnimationDefinition.CODEC.optionalFieldOf("animation").forGetter(HandheldDefinition::animation)
	).apply(instance, HandheldDefinition::new));

	/** HUD script name (the "hud" key). */
	public Optional<String> hud() {
		return this.presentation.hud();
	}

	/** Sound played when a reload starts (the "reload_sound" key). */
	public Optional<String> reloadSound() {
		return this.presentation.reloadSound();
	}

	/** "hud" and "reload_sound" - read from the definition's own JSON object (grouped only to keep the
	 * record within the codec builder's field limit). */
	public record Presentation(Optional<String> hud, Optional<String> reloadSound) {

		public static final com.mojang.serialization.MapCodec<Presentation> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				Codec.STRING.optionalFieldOf("hud").forGetter(Presentation::hud),
				Codec.STRING.optionalFieldOf("reload_sound").forGetter(Presentation::reloadSound)
		).apply(instance, Presentation::new));
	}

	/** Ticks to raise the weapon (0 for a weapon without an aim section - it fires at once). */
	public int raiseTicks() {
		return this.aim.map(AimSettings::raiseTicks).orElse(0);
	}

	/** Attachment slot names in a stable order (the workbench lists them in this order). */
	public List<String> attachmentSlotNames() {
		List<String> names = new java.util.ArrayList<>(this.attachments.keySet());
		java.util.Collections.sort(names);
		return names;
	}

	/** The mount for this attachment in this slot, or null if the slot doesn't accept it. */
	public AttachmentMount mountFor(String slot, Identifier attachmentId) {
		AttachmentSlot attachmentSlot = this.attachments.get(slot);
		return attachmentSlot == null ? null : attachmentSlot.accepts().get(attachmentId);
	}

	/** Model groups hidden for the given attachments: every group some mount shows (they're hidden
	 * unless that attachment is fitted), minus the ones the fitted attachments show, plus the ones
	 * the fitted attachments hide. See AttachmentMount. */
	public java.util.Set<String> hiddenGroups(Map<String, Identifier> fitted) {
		java.util.Set<String> hidden = new java.util.HashSet<>();
		for (AttachmentSlot slot : this.attachments.values()) {
			for (AttachmentMount mount : slot.accepts().values()) {
				hidden.addAll(mount.showGroups());
			}
		}
		for (Map.Entry<String, Identifier> entry : fitted.entrySet()) {
			AttachmentMount mount = mountFor(entry.getKey(), entry.getValue());
			if (mount != null) {
				hidden.removeAll(mount.showGroups());
			}
		}
		for (Map.Entry<String, Identifier> entry : fitted.entrySet()) {
			AttachmentMount mount = mountFor(entry.getKey(), entry.getValue());
			if (mount != null) {
				hidden.addAll(mount.hideGroups());
			}
		}
		return hidden;
	}

	/** First-person aiming. The model is drawn in camera space: lowered at the hip pose, and when
	 * aiming moved so sight_position (a point in model space, e.g. the rear sight notch) lands on the
	 * screen centre, eye_distance blocks in front of the eye. Arms, if given, are placed in model
	 * space (so they follow the weapon between the two poses).
	 * third_person_aiming, if set, replaces the third-person display transform while the holder is
	 * aiming (the raised two-handed pose); by default the ordinary one is used for both poses.
	 * sprint_translation/sprint_rotation: the first-person pose while sprinting (camera space, like
	 * hip_translation/hip_rotation) - by default carried across the body, muzzle to the left and down.
	 * third_person_sprinting, if set, replaces the third-person display transform while sprinting
	 * (the arms are put in a cross-body carry either way).
	 * raise_ticks: how long raising (and lowering) takes. A shot fired with use from the hip goes off
	 * once the weapon is fully up - the server waits this long too. */
	public record AimSettings(
			Vector3f sightPosition,
			float eyeDistance,
			float scale,
			Vector3f hipTranslation,
			Vector3f hipRotation,
			Optional<DisplayTransform> rightArm,
			Optional<DisplayTransform> leftArm,
			Optional<DisplayTransform> thirdPersonAiming,
		Vector3f sprintTranslation,
		Vector3f sprintRotation,
		Optional<DisplayTransform> thirdPersonSprinting,
		int raiseTicks
	) {

		public static final Codec<AimSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				VECTOR_3F.optionalFieldOf("sight_position", new Vector3f(0f, 0.1f, 0f)).forGetter(AimSettings::sightPosition),
				Codec.FLOAT.optionalFieldOf("eye_distance", 0.2f).forGetter(AimSettings::eyeDistance),
				Codec.FLOAT.optionalFieldOf("scale", 0.5f).forGetter(AimSettings::scale),
				VECTOR_3F.optionalFieldOf("hip_translation", new Vector3f(0.3f, -0.3f, -0.45f)).forGetter(AimSettings::hipTranslation),
				VECTOR_3F.optionalFieldOf("hip_rotation", new Vector3f()).forGetter(AimSettings::hipRotation),
				DisplayTransform.CODEC.optionalFieldOf("right_arm").forGetter(AimSettings::rightArm),
				DisplayTransform.CODEC.optionalFieldOf("left_arm").forGetter(AimSettings::leftArm),
				DisplayTransform.CODEC.optionalFieldOf("third_person_aiming").forGetter(AimSettings::thirdPersonAiming),
				VECTOR_3F.optionalFieldOf("sprint_translation", new Vector3f(0.1f, -0.36f, -0.42f)).forGetter(AimSettings::sprintTranslation),
				VECTOR_3F.optionalFieldOf("sprint_rotation", new Vector3f(-20f, 60f, 20f)).forGetter(AimSettings::sprintRotation),
				DisplayTransform.CODEC.optionalFieldOf("third_person_sprinting").forGetter(AimSettings::thirdPersonSprinting),
				Codec.intRange(0, 200).optionalFieldOf("raise_ticks", 4).forGetter(AimSettings::raiseTicks)
		).apply(instance, AimSettings::new));
	}

	/** One attachment slot ("optic", "muzzle", ...): which attachments fit, and how each one sits. */
	public record AttachmentSlot(Map<Identifier, AttachmentMount> accepts) {

		public static final Codec<AttachmentSlot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.unboundedMap(Identifier.CODEC, AttachmentMount.CODEC).fieldOf("accepts").forGetter(AttachmentSlot::accepts)
		).apply(instance, AttachmentSlot::new));
	}

	/** How one attachment is shown on this weapon.
	 * model/texture: a separate OBJ drawn at transform (in the weapon's model space); texture defaults
	 * to the weapon's own. show_groups: groups of the WEAPON's own OBJ that only appear with this
	 * attachment fitted (an alternative to a separate model). hide_groups: weapon groups hidden while
	 * it's fitted (e.g. the iron sights under a scope, or the standard magazine). sight_position: if
	 * set, replaces the weapon's aim.sight_position while fitted. part: the animated part (see
 * AnimationDefinition) a separate model moves with - "root" (the whole weapon) by default. */
	public record AttachmentMount(
			Optional<Identifier> model,
			Optional<Identifier> texture,
			DisplayTransform transform,
			List<String> showGroups,
			List<String> hideGroups,
			Optional<Vector3f> sightPosition,
		Optional<String> part
	) {

		public static final Codec<AttachmentMount> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Identifier.CODEC.optionalFieldOf("model").forGetter(AttachmentMount::model),
				Identifier.CODEC.optionalFieldOf("texture").forGetter(AttachmentMount::texture),
				DisplayTransform.CODEC.optionalFieldOf("transform", DisplayTransform.IDENTITY).forGetter(AttachmentMount::transform),
				Codec.STRING.listOf().optionalFieldOf("show_groups", List.of()).forGetter(AttachmentMount::showGroups),
				Codec.STRING.listOf().optionalFieldOf("hide_groups", List.of()).forGetter(AttachmentMount::hideGroups),
				VECTOR_3F.optionalFieldOf("sight_position").forGetter(AttachmentMount::sightPosition),
				Codec.STRING.optionalFieldOf("part").forGetter(AttachmentMount::part)
		).apply(instance, AttachmentMount::new));
	}

	/** SEMI fires once per press of the use key, AUTO keeps firing while it's held (at the weapon file's own Delay). */
	public enum FireMode {
		SEMI, AUTO;

		public static final Codec<FireMode> CODEC = Codec.STRING.xmap(
				s -> "auto".equalsIgnoreCase(s) ? AUTO : SEMI,
				mode -> mode.name().toLowerCase(java.util.Locale.ROOT));
	}

	/** Extra model transform for one ItemDisplayContext, applied on top of the base item model's own
	 * display transform: translate, then rotate (degrees, X then Y then Z), then scale. */
	public record DisplayTransform(Vector3f translation, Vector3f rotation, Vector3f scale) {

		public static final DisplayTransform IDENTITY =
				new DisplayTransform(new Vector3f(), new Vector3f(), new Vector3f(1f, 1f, 1f));

		public static final Codec<DisplayTransform> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				VECTOR_3F.optionalFieldOf("translation", new Vector3f()).forGetter(DisplayTransform::translation),
				VECTOR_3F.optionalFieldOf("rotation", new Vector3f()).forGetter(DisplayTransform::rotation),
				VECTOR_3F.optionalFieldOf("scale", new Vector3f(1f, 1f, 1f)).forGetter(DisplayTransform::scale)
		).apply(instance, DisplayTransform::new));
	}
}
