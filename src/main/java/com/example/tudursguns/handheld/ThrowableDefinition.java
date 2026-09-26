package com.example.tudursguns.handheld;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Optional;

/** One throwable (grenade, smoke, flash, ...), read from data/<namespace>/throwable/<name>.json.
 *
 * What flies and what it does on impact/explosion comes from a weapon file, like a handheld weapon:
 * Power, Explosion, Gravity, Bound (bounce), Bomblet (fragments), ModelBullet, Sound. The thrown
 * object is Tudur's Vehicle Mod's own projectile, so an explosion damages vehicles exactly as a
 * vehicle weapon's would. This file adds how it's thrown (speed, fuse, cooking) and any effect
 * other than an explosion (smoke, flash, fire). */
public record ThrowableDefinition(
		String weapon,
		Optional<String> displayName,
		Optional<Identifier> model,
		Optional<Identifier> texture,
		Map<ItemDisplayContext, HandheldDefinition.DisplayTransform> display,
		float throwVelocity,
		float underhandVelocity,
		int fuseTicks,
		boolean cookable,
		Effect effect,
		Optional<String> pinSound
) {

	public static final Codec<ThrowableDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("weapon").forGetter(ThrowableDefinition::weapon),
			Codec.STRING.optionalFieldOf("display_name").forGetter(ThrowableDefinition::displayName),
			Identifier.CODEC.optionalFieldOf("model").forGetter(ThrowableDefinition::model),
			Identifier.CODEC.optionalFieldOf("texture").forGetter(ThrowableDefinition::texture),
			Codec.unboundedMap(ItemDisplayContext.CODEC, HandheldDefinition.DisplayTransform.CODEC).optionalFieldOf("display", Map.of()).forGetter(ThrowableDefinition::display),
			Codec.floatRange(0f, 10f).optionalFieldOf("throw_velocity", 1.2f).forGetter(ThrowableDefinition::throwVelocity),
			Codec.floatRange(0f, 10f).optionalFieldOf("underhand_velocity", 0.5f).forGetter(ThrowableDefinition::underhandVelocity),
			Codec.intRange(0, 72000).optionalFieldOf("fuse_ticks", 80).forGetter(ThrowableDefinition::fuseTicks),
			Codec.BOOL.optionalFieldOf("cookable", true).forGetter(ThrowableDefinition::cookable),
			Effect.CODEC.optionalFieldOf("effect", Effect.NONE).forGetter(ThrowableDefinition::effect),
			Codec.STRING.optionalFieldOf("pin_sound").forGetter(ThrowableDefinition::pinSound)
	).apply(instance, ThrowableDefinition::new));

	/** What happens where it ends up, besides the weapon file's own explosion.
	 * type: none | smoke | signal | flash | incendiary.
	 * smoke: a cloud of radius blocks for duration_ticks that hides what's in or behind it and draws
	 * missile locks onto itself. signal: a coloured column of smoke, purely a marker. flash: blinds
	 * players within radius who can see it (more the more directly they look at it) and makes mobs
	 * lose their target, for up to duration_ticks. incendiary: sets fire within radius. */
	public record Effect(Type type, float radius, int durationTicks, Vector3f color) {

		public static final Effect NONE = new Effect(Type.NONE, 0f, 0, new Vector3f(0.6f, 0.6f, 0.6f));

		public static final Codec<Effect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Type.CODEC.fieldOf("type").forGetter(Effect::type),
				Codec.floatRange(0f, 64f).optionalFieldOf("radius", 4f).forGetter(Effect::radius),
				Codec.intRange(0, 72000).optionalFieldOf("duration_ticks", 400).forGetter(Effect::durationTicks),
				HandheldDefinition.VECTOR_3F.optionalFieldOf("color", new Vector3f(0.6f, 0.6f, 0.6f)).forGetter(Effect::color)
		).apply(instance, Effect::new));

		/** color is [r, g, b], each 0-1. */
		public int rgb() {
			int r = Math.round(Math.max(0f, Math.min(1f, this.color.x())) * 255f);
			int g = Math.round(Math.max(0f, Math.min(1f, this.color.y())) * 255f);
			int b = Math.round(Math.max(0f, Math.min(1f, this.color.z())) * 255f);
			return (r << 16) | (g << 8) | b;
		}
	}

	public enum Type {
		NONE, SMOKE, SIGNAL, FLASH, INCENDIARY;

		public static final Codec<Type> CODEC = Codec.STRING.xmap(
				s -> switch (s.toLowerCase(java.util.Locale.ROOT)) {
					case "smoke" -> SMOKE;
					case "signal" -> SIGNAL;
					case "flash" -> FLASH;
					case "incendiary" -> INCENDIARY;
					default -> NONE;
				},
				type -> type.name().toLowerCase(java.util.Locale.ROOT));
	}
}
