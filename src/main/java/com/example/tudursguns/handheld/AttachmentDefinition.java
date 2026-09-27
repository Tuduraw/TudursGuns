package com.example.tudursguns.handheld;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Optional;

/** One attachment (scope, silencer, magazine, bayonet, ...), read from
 * data/<namespace>/attachment/<name>.json.
 *
 * This file says what the attachment DOES and how it looks as an item. Where it sits on a particular
 * weapon, and which model it uses there, is up to each weapon's own definition (its "attachments"
 * section) - the same scope can sit at a different height on every rifle. */
public record AttachmentDefinition(
		String displayName,
		Optional<Identifier> model,
		Optional<Identifier> texture,
		Map<ItemDisplayContext, HandheldDefinition.DisplayTransform> display,
		float magazineSizeMultiplier,
		int magazineSizeBonus,
		float reloadTimeMultiplier,
		Optional<String> soundOverride,
		float soundVolumeMultiplier,
		float soundPitchMultiplier,
		float accuracyMultiplier,
		float meleeDamageBonus,
		Optional<Zoom> zoom,
		Optional<Underbarrel> underbarrel,
		HeldMovement movement,
		Tuning tuning
) {

	public static final Codec<AttachmentDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("display_name").forGetter(AttachmentDefinition::displayName),
			Identifier.CODEC.optionalFieldOf("model").forGetter(AttachmentDefinition::model),
			Identifier.CODEC.optionalFieldOf("texture").forGetter(AttachmentDefinition::texture),
			DefinitionCodecs.DISPLAY.forGetter(AttachmentDefinition::display),
			Codec.floatRange(0.0f, 100.0f).optionalFieldOf("magazine_size_multiplier", 1.0f).forGetter(AttachmentDefinition::magazineSizeMultiplier),
			Codec.INT.optionalFieldOf("magazine_size_bonus", 0).forGetter(AttachmentDefinition::magazineSizeBonus),
			Codec.floatRange(0.0f, 100.0f).optionalFieldOf("reload_time_multiplier", 1.0f).forGetter(AttachmentDefinition::reloadTimeMultiplier),
			Codec.STRING.optionalFieldOf("sound_override").forGetter(AttachmentDefinition::soundOverride),
			Codec.floatRange(0.0f, 100.0f).optionalFieldOf("sound_volume_multiplier", 1.0f).forGetter(AttachmentDefinition::soundVolumeMultiplier),
			Codec.floatRange(0.0f, 100.0f).optionalFieldOf("sound_pitch_multiplier", 1.0f).forGetter(AttachmentDefinition::soundPitchMultiplier),
			Codec.floatRange(0.0f, 100.0f).optionalFieldOf("accuracy_multiplier", 1.0f).forGetter(AttachmentDefinition::accuracyMultiplier),
			Codec.FLOAT.optionalFieldOf("melee_damage_bonus", 0.0f).forGetter(AttachmentDefinition::meleeDamageBonus),
			Zoom.CODEC.optionalFieldOf("zoom").forGetter(AttachmentDefinition::zoom),
			Underbarrel.CODEC.optionalFieldOf("underbarrel").forGetter(AttachmentDefinition::underbarrel),
			HeldMovement.MAP_CODEC.forGetter(AttachmentDefinition::movement),
			Tuning.MAP_CODEC.forGetter(AttachmentDefinition::tuning)
	).apply(instance, AttachmentDefinition::new));

	public float recoilMultiplier() {
		return this.tuning.recoilMultiplier();
	}

	public float muzzleFlashMultiplier() {
		return this.tuning.muzzleFlashMultiplier();
	}

	/** "recoil_multiplier" and "muzzle_flash_multiplier" (the flash's size - 0 hides it, as a
	 * suppressor does) - read from the attachment's own JSON object (grouped only to keep the record
	 * within the codec builder's field limit). */
	public record Tuning(float recoilMultiplier, float muzzleFlashMultiplier) {

		public static final com.mojang.serialization.MapCodec<Tuning> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				Codec.floatRange(0f, 100f).optionalFieldOf("recoil_multiplier", 1f).forGetter(Tuning::recoilMultiplier),
				Codec.floatRange(0f, 100f).optionalFieldOf("muzzle_flash_multiplier", 1f).forGetter(Tuning::muzzleFlashMultiplier)
		).apply(instance, Tuning::new));
	}

	/** A second weapon under the barrel (a grenade launcher): the weapon it's fitted to can switch
	 * between its own fire and this one (the underbarrel switch key). It has its own weapon file,
	 * rounds (loaded separately - the weapon file's Round), ammo item and projectile item, and fires
	 * one shot per press. muzzle_offset defaults to the weapon's own. */
	public record Underbarrel(String weapon, Identifier projectileItem, Optional<Identifier> ammoItem, int roundsPerAmmoItem,
			Optional<String> reloadSound, Optional<org.joml.Vector3f> muzzleOffset) {

		public static final Codec<Underbarrel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("weapon").forGetter(Underbarrel::weapon),
				Identifier.CODEC.optionalFieldOf("projectile_item", Identifier.ofVanilla("iron_nugget")).forGetter(Underbarrel::projectileItem),
				Identifier.CODEC.optionalFieldOf("ammo_item").forGetter(Underbarrel::ammoItem),
				Codec.intRange(1, 1_000_000).optionalFieldOf("rounds_per_ammo_item", 1).forGetter(Underbarrel::roundsPerAmmoItem),
				Codec.STRING.optionalFieldOf("reload_sound").forGetter(Underbarrel::reloadSound),
				HandheldDefinition.VECTOR_3F.optionalFieldOf("muzzle_offset").forGetter(Underbarrel::muzzleOffset)
		).apply(instance, Underbarrel::new));
	}

	/** Scope magnification. The player looks through the scope while holding the aim key; the mouse
	 * wheel changes magnification between min and max in steps of step. overlay is the texture drawn
	 * over the screen while looking through (defaults to the spyglass's). */
	public record Zoom(float min, float max, float defaultMagnification, float step, Optional<Identifier> overlay) {

		public static final Identifier DEFAULT_OVERLAY = Identifier.ofVanilla("textures/misc/spyglass_scope.png");

		public static final Codec<Zoom> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.floatRange(1.0f, 100.0f).optionalFieldOf("min", 2.0f).forGetter(Zoom::min),
				Codec.floatRange(1.0f, 100.0f).optionalFieldOf("max", 8.0f).forGetter(Zoom::max),
				Codec.floatRange(1.0f, 100.0f).optionalFieldOf("default", 4.0f).forGetter(Zoom::defaultMagnification),
				Codec.floatRange(0.01f, 100.0f).optionalFieldOf("step", 1.0f).forGetter(Zoom::step),
				Identifier.CODEC.optionalFieldOf("overlay").forGetter(Zoom::overlay)
		).apply(instance, Zoom::new));

		public float clamp(float magnification) {
			float low = Math.min(this.min, this.max);
			float high = Math.max(this.min, this.max);
			return Math.max(low, Math.min(high, magnification));
		}
	}
}
