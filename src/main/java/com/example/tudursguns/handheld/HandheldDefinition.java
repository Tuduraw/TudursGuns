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
		Handling handling,
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
			DefinitionCodecs.DISPLAY.forGetter(HandheldDefinition::display),
			AimSettings.CODEC.optionalFieldOf("aim").forGetter(HandheldDefinition::aim),
			Codec.unboundedMap(Codec.STRING, AttachmentSlot.CODEC).optionalFieldOf("attachments", Map.of()).forGetter(HandheldDefinition::attachments),
			Handling.MAP_CODEC.forGetter(HandheldDefinition::handling),
			AnimationDefinition.CODEC.optionalFieldOf("animation").forGetter(HandheldDefinition::animation)
	).apply(instance, HandheldDefinition::new));

	/** movement_speed / aiming_movement_speed (see HeldMovement). */
	public HeldMovement movement() {
		return this.handling.movement();
	}

	/** How the weapon handles, beyond its weapon file - read from the definition's own JSON object:
	 * recoil: degrees the view kicks up per shot (and up to a quarter of that sideways, at random);
	 *   recoil_sneaking: the same while sneaking (default: half of recoil). Attachments scale it.
	 * ads_spread_multiplier: spread (the weapon file's Accuracy) is multiplied by this while aiming
	 *   with the aim key (a deliberate aim, as opposed to a quick shot with use).
	 * pellets: projectiles per shot (a shotgun) - one round, one sound, each pellet spread separately.
	 * burst_count: shots per trigger press for fire_mode "burst" (at the weapon file's Delay apart).
	 * melee_damage: added to the holder's attack damage while it's in the main hand (a rifle butt).
	 * icon: a flat picture shown in inventories instead of the model (a PNG, e.g.
	 *   "ns:textures/vehicle/icons/rifle.png").
	 * ammo: an ammo definition (data/<ns>/ammo/) to reload from - a magazine item giving its own
	 *   number of rounds. Takes the place of ammo_item / rounds_per_ammo_item. */
	public record Handling(HeldMovement movement, float recoil, Optional<Float> recoilSneaking, float adsSpreadMultiplier,
			int pellets, int burstCount, float meleeDamage, Optional<Identifier> icon, Optional<Identifier> ammo) {

		public static final com.mojang.serialization.MapCodec<Handling> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				HeldMovement.MAP_CODEC.forGetter(Handling::movement),
				Codec.floatRange(0f, 90f).optionalFieldOf("recoil", 0f).forGetter(Handling::recoil),
				Codec.floatRange(0f, 90f).optionalFieldOf("recoil_sneaking").forGetter(Handling::recoilSneaking),
				Codec.floatRange(0f, 100f).optionalFieldOf("ads_spread_multiplier", 1f).forGetter(Handling::adsSpreadMultiplier),
				Codec.intRange(1, 64).optionalFieldOf("pellets", 1).forGetter(Handling::pellets),
				Codec.intRange(1, 100).optionalFieldOf("burst_count", 3).forGetter(Handling::burstCount),
				Codec.floatRange(0f, 10000f).optionalFieldOf("melee_damage", 0f).forGetter(Handling::meleeDamage),
				Identifier.CODEC.optionalFieldOf("icon").forGetter(Handling::icon),
				Identifier.CODEC.optionalFieldOf("ammo").forGetter(Handling::ammo)
		).apply(instance, Handling::new));

		/** The same, reloading from something else (an underbarrel launcher has its own ammo). */
		public Handling withoutAmmo() {
			return new Handling(this.movement, this.recoil, this.recoilSneaking, this.adsSpreadMultiplier, this.pellets,
					this.burstCount, this.meleeDamage, this.icon, Optional.empty());
		}

		public float recoilFor(boolean sneaking) {
			return sneaking ? this.recoilSneaking.orElse(this.recoil / 2f) : this.recoil;
		}
	}

	/** HUD script name (the "hud" key). */
	public Optional<String> hud() {
		return this.presentation.hud();
	}

	/** Sound played when a reload starts (the "reload_sound" key). */
	public Optional<String> reloadSound() {
		return this.presentation.reloadSound();
	}

	/** Muzzle flash, thrown-out cartridges and magazines (the "effects" key - see HandheldEffects). */
	public HandheldEffects effects() {
		return this.presentation.effects();
	}

	/** "hud", "reload_sound" and "effects" - read from the definition's own JSON object (grouped only
	 * to keep the record within the codec builder's field limit). */
	public record Presentation(Optional<String> hud, Optional<String> reloadSound, HandheldEffects effects) {

		public static final com.mojang.serialization.MapCodec<Presentation> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				Codec.STRING.optionalFieldOf("hud").forGetter(Presentation::hud),
				Codec.STRING.optionalFieldOf("reload_sound").forGetter(Presentation::reloadSound),
				HandheldEffects.CODEC.optionalFieldOf("effects", HandheldEffects.AUTO).forGetter(Presentation::effects)
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
	 * zoom: magnification while aimed down the iron sights (1 = none).
	 * scope: a built-in scope (same form as a scope attachment's zoom), looked through with the aim
	 *   key when no scope attachment is fitted.
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
		int raiseTicks,
		float zoom,
		Optional<AttachmentDefinition.Zoom> scope
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
				Codec.intRange(0, 200).optionalFieldOf("raise_ticks", 4).forGetter(AimSettings::raiseTicks),
				Codec.floatRange(1f, 100f).optionalFieldOf("zoom", 1f).forGetter(AimSettings::zoom),
				AttachmentDefinition.Zoom.CODEC.optionalFieldOf("scope").forGetter(AimSettings::scope)
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
 * AnimationDefinition) a separate model moves with - "root" (the whole weapon) by default.
 * sound_override: this weapon's own firing sound with the attachment (a suppressed shot) - takes
 * precedence over the attachment's general sound_override. */
	public record AttachmentMount(
			Optional<Identifier> model,
			Optional<Identifier> texture,
			DisplayTransform transform,
			List<String> showGroups,
			List<String> hideGroups,
			Optional<Vector3f> sightPosition,
		Optional<String> part,
		Optional<String> soundOverride
	) {

		public static final Codec<AttachmentMount> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Identifier.CODEC.optionalFieldOf("model").forGetter(AttachmentMount::model),
				Identifier.CODEC.optionalFieldOf("texture").forGetter(AttachmentMount::texture),
				DisplayTransform.CODEC.optionalFieldOf("transform", DisplayTransform.IDENTITY).forGetter(AttachmentMount::transform),
				Codec.STRING.listOf().optionalFieldOf("show_groups", List.of()).forGetter(AttachmentMount::showGroups),
				Codec.STRING.listOf().optionalFieldOf("hide_groups", List.of()).forGetter(AttachmentMount::hideGroups),
				VECTOR_3F.optionalFieldOf("sight_position").forGetter(AttachmentMount::sightPosition),
				Codec.STRING.optionalFieldOf("part").forGetter(AttachmentMount::part),
				Codec.STRING.optionalFieldOf("sound_override").forGetter(AttachmentMount::soundOverride)
		).apply(instance, AttachmentMount::new));
	}

	/** SEMI fires once per press of the use key, AUTO keeps firing while it's held (at the weapon file's
	 * own Delay), BURST fires burst_count shots per press. */
	public enum FireMode {
		SEMI, AUTO, BURST;

		public static final Codec<FireMode> CODEC = DefinitionCodecs.lenientEnum(FireMode.class, SEMI);
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
