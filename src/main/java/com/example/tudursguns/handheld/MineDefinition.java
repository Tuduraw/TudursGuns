package com.example.tudursguns.handheld;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Identifier;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** One placeable explosive (mine, claymore, demolition charge), read from data/<namespace>/mine/<name>.json.
 *
 * The explosion is weapon's own (the weapon file's Explosion/Power/...), set off by a Tudur's Vehicle
 * Mod projectile at the charge, so it damages vehicles exactly like a vehicle weapon's explosion.
 * A placed charge is visible to everyone and goes off for anyone - there is no owner or team check
 * on what sets it off.
 *
 * trigger: proximity (anything allowed by trigger_living/trigger_vehicles within trigger_radius of
 *   it), directional (the same, but only inside the fragments' cone in front of it and in line of
 *   sight - a claymore), or remote (only a detonator - see EquipmentDefinition).
 * placement: ground (on top of a block), surface (any face of a block), anywhere (any face, or stuck
 *   to a vehicle - sneak and use it on the vehicle; it then moves with the vehicle).
 * arming_ticks: after placing, before it can go off. trigger_delay_ticks: from being set off to
 *   exploding (the click of a pressure fuse).
 * fragments: projectiles fired from the charge when it goes off, from their own weapon file, spread
 *   over spread_degrees around the direction the placer faced.
 * defuse_ticks: how long a defuse kit takes. The placer can also pick it back up (sneak + use).
 * placed: model transform when placed (the model's +Y points out of the surface). */
public record MineDefinition(
		String weapon,
		Optional<String> displayName,
		Optional<Identifier> model,
		Optional<Identifier> texture,
		Map<ItemDisplayContext, HandheldDefinition.DisplayTransform> display,
		HandheldDefinition.DisplayTransform placed,
		Trigger trigger,
		float triggerRadius,
		SetOffBy setOffBy,
		int armingTicks,
		int triggerDelayTicks,
		Placement placement,
		Optional<Fragments> fragments,
		int defuseTicks,
		Sounds sounds,
		HeldMovement movement
) {

	public static final Codec<MineDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("weapon").forGetter(MineDefinition::weapon),
			Codec.STRING.optionalFieldOf("display_name").forGetter(MineDefinition::displayName),
			Identifier.CODEC.optionalFieldOf("model").forGetter(MineDefinition::model),
			Identifier.CODEC.optionalFieldOf("texture").forGetter(MineDefinition::texture),
			Codec.unboundedMap(ItemDisplayContext.CODEC, HandheldDefinition.DisplayTransform.CODEC).optionalFieldOf("display", Map.of()).forGetter(MineDefinition::display),
			HandheldDefinition.DisplayTransform.CODEC.optionalFieldOf("placed", HandheldDefinition.DisplayTransform.IDENTITY).forGetter(MineDefinition::placed),
			Trigger.CODEC.optionalFieldOf("trigger", Trigger.PROXIMITY).forGetter(MineDefinition::trigger),
			Codec.floatRange(0f, 32f).optionalFieldOf("trigger_radius", 1.0f).forGetter(MineDefinition::triggerRadius),
			SetOffBy.MAP_CODEC.forGetter(MineDefinition::setOffBy),
			Codec.intRange(0, 72000).optionalFieldOf("arming_ticks", 60).forGetter(MineDefinition::armingTicks),
			Codec.intRange(0, 200).optionalFieldOf("trigger_delay_ticks", 0).forGetter(MineDefinition::triggerDelayTicks),
			Placement.CODEC.optionalFieldOf("placement", Placement.GROUND).forGetter(MineDefinition::placement),
			Fragments.CODEC.optionalFieldOf("fragments").forGetter(MineDefinition::fragments),
			Codec.intRange(0, 72000).optionalFieldOf("defuse_ticks", 60).forGetter(MineDefinition::defuseTicks),
			Sounds.CODEC.optionalFieldOf("sounds", Sounds.NONE).forGetter(MineDefinition::sounds),
			HeldMovement.MAP_CODEC.forGetter(MineDefinition::movement)
	).apply(instance, MineDefinition::new));

	public boolean triggerLiving() {
		return this.setOffBy.living();
	}

	public boolean triggerVehicles() {
		return this.setOffBy.vehicles();
	}

	/** trigger_living / trigger_vehicles (read from the definition's own JSON object). */
	public record SetOffBy(boolean living, boolean vehicles) {

		public static final MapCodec<SetOffBy> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				Codec.BOOL.optionalFieldOf("trigger_living", true).forGetter(SetOffBy::living),
				Codec.BOOL.optionalFieldOf("trigger_vehicles", true).forGetter(SetOffBy::vehicles)
		).apply(instance, SetOffBy::new));
	}

	public record Fragments(String weapon, int count, float spreadDegrees) {

		public static final Codec<Fragments> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("weapon").forGetter(Fragments::weapon),
				Codec.intRange(1, 200).optionalFieldOf("count", 20).forGetter(Fragments::count),
				Codec.floatRange(0f, 180f).optionalFieldOf("spread_degrees", 60f).forGetter(Fragments::spreadDegrees)
		).apply(instance, Fragments::new));
	}

	/** Named .ogg sounds (like a weapon file's Sound): armed, set off (before trigger_delay_ticks), placed. */
	public record Sounds(Optional<String> arm, Optional<String> trigger, Optional<String> place) {

		public static final Sounds NONE = new Sounds(Optional.empty(), Optional.empty(), Optional.empty());

		public static final Codec<Sounds> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.optionalFieldOf("arm").forGetter(Sounds::arm),
				Codec.STRING.optionalFieldOf("trigger").forGetter(Sounds::trigger),
				Codec.STRING.optionalFieldOf("place").forGetter(Sounds::place)
		).apply(instance, Sounds::new));
	}

	public enum Trigger {
		PROXIMITY, DIRECTIONAL, REMOTE;

		public static final Codec<Trigger> CODEC = Codec.STRING.xmap(
				s -> switch (s.toLowerCase(Locale.ROOT)) {
					case "directional" -> DIRECTIONAL;
					case "remote" -> REMOTE;
					default -> PROXIMITY;
				},
				trigger -> trigger.name().toLowerCase(Locale.ROOT));
	}

	public enum Placement {
		GROUND, SURFACE, ANYWHERE;

		public static final Codec<Placement> CODEC = Codec.STRING.xmap(
				s -> switch (s.toLowerCase(Locale.ROOT)) {
					case "surface" -> SURFACE;
					case "anywhere" -> ANYWHERE;
					default -> GROUND;
				},
				placement -> placement.name().toLowerCase(Locale.ROOT));
	}
}
