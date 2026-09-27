package com.example.tudursguns.handheld;

import com.example.tudursguns.TudursGuns;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.Optional;

/** The "effects" object of a handheld definition: what a shot and a reload look like around the
 * weapon - a muzzle flash, spent cartridges thrown out, an empty magazine dropped.
 *
 * Each of the three can be left out (the default for the weapon's type: guns - weapon files of
 * Type MachineGun - get all three, rockets and missiles only the flash), set to false (none), set
 * to true (the default settings), or given as an object whose missing keys take the defaults. */
public record HandheldEffects(Optional<MuzzleFlash> muzzleFlash, Optional<Ejection> cartridge, Optional<Ejection> magazine) {

	/** Nothing specified: every effect follows the weapon's type. */
	public static final HandheldEffects AUTO = new HandheldEffects(Optional.empty(), Optional.empty(), Optional.empty());

	/** A colour as "#RRGGBB" or a number. */
	static final Codec<Integer> COLOR = Codec.either(Codec.INT, Codec.STRING.comapFlatMap(text -> {
		String hex = text.startsWith("#") ? text.substring(1) : text;
		try {
			return DataResult.success(Integer.parseInt(hex, 16) & 0xFFFFFF);
		} catch (NumberFormatException e) {
			return DataResult.error(() -> "Not a colour: " + text);
		}
	}, color -> String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF))).xmap(
			either -> either.map(value -> value, value -> value),
			Either::right);

	public static final Codec<HandheldEffects> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			MuzzleFlash.CODEC.optionalFieldOf("muzzle_flash").forGetter(HandheldEffects::muzzleFlash),
			Ejection.CARTRIDGE_CODEC.optionalFieldOf("cartridge").forGetter(HandheldEffects::cartridge),
			Ejection.MAGAZINE_CODEC.optionalFieldOf("magazine").forGetter(HandheldEffects::magazine)
	).apply(instance, HandheldEffects::new));

	/** false -> off, true -> on, an object -> as given. */
	static <T> Codec<T> toggle(Codec<T> object, T on, T off) {
		return Codec.either(Codec.BOOL, object).xmap(
				either -> either.map(enabled -> enabled ? on : off, value -> value),
				Either::right);
	}


	/** A flash of coloured particles at the muzzle, and a little smoke.
	 * size: particle size (up to 4); count: flash particles; smoke: smoke particles; distance: how far
	 * ahead of the muzzle (muzzle_offset) the flash is. Attachments can dim or hide it
	 * (muzzle_flash_multiplier - a suppressor has 0). */
	public record MuzzleFlash(boolean enabled, float size, int color, int count, int smoke, float distance) {

		public static final MuzzleFlash DEFAULT = new MuzzleFlash(true, 1.2f, 0xFFD27F, 3, 2, 0.1f);
		public static final MuzzleFlash OFF = new MuzzleFlash(false, 0f, 0, 0, 0, 0f);

		private static final Codec<MuzzleFlash> OBJECT = RecordCodecBuilder.create(instance -> instance.group(
				Codec.BOOL.optionalFieldOf("enabled", true).forGetter(MuzzleFlash::enabled),
				Codec.floatRange(0.01f, 4f).optionalFieldOf("size", DEFAULT.size).forGetter(MuzzleFlash::size),
				COLOR.optionalFieldOf("color", DEFAULT.color).forGetter(MuzzleFlash::color),
				Codec.intRange(0, 64).optionalFieldOf("count", DEFAULT.count).forGetter(MuzzleFlash::count),
				Codec.intRange(0, 64).optionalFieldOf("smoke", DEFAULT.smoke).forGetter(MuzzleFlash::smoke),
				Codec.floatRange(-4f, 4f).optionalFieldOf("distance", DEFAULT.distance).forGetter(MuzzleFlash::distance)
		).apply(instance, MuzzleFlash::new));

		public static final Codec<MuzzleFlash> CODEC = toggle(OBJECT, DEFAULT, OFF);
	}

	/** When a thrown-out object comes out. */
	public enum Trigger {
		/** With each shot. */
		FIRE,
		/** When a reload starts. */
		RELOAD;

		public static final Codec<Trigger> CODEC = Codec.STRING.xmap(
				s -> "reload".equalsIgnoreCase(s) ? RELOAD : FIRE,
				trigger -> trigger.name().toLowerCase(Locale.ROOT));
	}

	/** Something thrown out of the weapon - a spent cartridge or an empty magazine. It's a small model
	 * that falls, bounces and disappears lifetime ticks after it first lands; it hits nothing and
	 * isn't saved with the world.
	 *
	 * type: a built-in model - "rifle", "pistol", "shotgun", "large" (grenade) cartridges, or
	 *   "magazine". model / texture replace it (an OBJ and its PNG, like a weapon's).
	 * scale: model size.
	 * offset: where it comes out - [right, up, forward] from the eye, like muzzle_offset (mirrored in
	 *   the left hand). Defaults to a point along muzzle_offset (the ejection port / magazine well).
	 * velocity: [right, up, forward] blocks per tick, plus up to velocity_random in any direction and
	 *   the shooter's own movement.
	 * gravity, bounce: fall acceleration and how much speed a bounce keeps (0 = stops on landing).
	 * on: "fire" (with each shot) or "reload" (when a reload starts).
	 * delay: ticks after the shot / reload start (a bolt action throws its case out when the bolt
	 *   opens).
	 * count: how many; for on = "reload", 0 means one per round fired since the last reload (a
	 *   revolver emptying its cylinder, up to 12).
	 * lifetime: ticks it stays after landing. */
	public record Ejection(boolean enabled, String type, Optional<Identifier> model, Optional<Identifier> texture, float scale,
			Optional<Vector3f> offset, Vector3f velocity, float velocityRandom, float gravity, float bounce, Trigger on, int delay,
			int count, int lifetime) {

		public static final Ejection CARTRIDGE = new Ejection(true, "rifle", Optional.empty(), Optional.empty(), 1f,
				Optional.empty(), new Vector3f(0.14f, 0.12f, -0.02f), 0.03f, 0.04f, 0.35f, Trigger.FIRE, 0, 1, 40);
		public static final Ejection MAGAZINE = new Ejection(true, "magazine", Optional.empty(), Optional.empty(), 1f,
				Optional.empty(), new Vector3f(0f, -0.02f, 0.02f), 0.01f, 0.05f, 0.15f, Trigger.RELOAD, 0, 1, 100);
		public static final Ejection OFF = new Ejection(false, "rifle", Optional.empty(), Optional.empty(), 1f,
				Optional.empty(), new Vector3f(), 0f, 0f, 0f, Trigger.FIRE, 0, 0, 0);

		private static Codec<Ejection> object(Ejection defaults) {
			return RecordCodecBuilder.create(instance -> instance.group(
					Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Ejection::enabled),
					Codec.STRING.optionalFieldOf("type", defaults.type).forGetter(Ejection::type),
					Identifier.CODEC.optionalFieldOf("model").forGetter(Ejection::model),
					Identifier.CODEC.optionalFieldOf("texture").forGetter(Ejection::texture),
					Codec.floatRange(0.01f, 100f).optionalFieldOf("scale", defaults.scale).forGetter(Ejection::scale),
					HandheldDefinition.VECTOR_3F.optionalFieldOf("offset").forGetter(Ejection::offset),
					HandheldDefinition.VECTOR_3F.optionalFieldOf("velocity", defaults.velocity).forGetter(Ejection::velocity),
					Codec.floatRange(0f, 10f).optionalFieldOf("velocity_random", defaults.velocityRandom).forGetter(Ejection::velocityRandom),
					Codec.floatRange(0f, 10f).optionalFieldOf("gravity", defaults.gravity).forGetter(Ejection::gravity),
					Codec.floatRange(0f, 1f).optionalFieldOf("bounce", defaults.bounce).forGetter(Ejection::bounce),
					Trigger.CODEC.optionalFieldOf("on", defaults.on).forGetter(Ejection::on),
					Codec.intRange(0, 200).optionalFieldOf("delay", defaults.delay).forGetter(Ejection::delay),
					Codec.intRange(0, 64).optionalFieldOf("count", defaults.count).forGetter(Ejection::count),
					Codec.intRange(1, 6000).optionalFieldOf("lifetime", defaults.lifetime).forGetter(Ejection::lifetime)
			).apply(instance, Ejection::new));
		}

		public static final Codec<Ejection> CARTRIDGE_CODEC = toggle(object(CARTRIDGE), CARTRIDGE, OFF);
		public static final Codec<Ejection> MAGAZINE_CODEC = toggle(object(MAGAZINE), MAGAZINE, OFF);

		/** The OBJ: model, else the built-in one for type. */
		public Identifier modelId() {
			return this.model.orElseGet(() -> Identifier.of(TudursGuns.MOD_ID, "models/obj/" + builtInName() + ".obj"));
		}

		/** The texture: texture, else the built-in models' shared one. */
		public Identifier textureId() {
			return this.texture.orElseGet(() -> Identifier.of(TudursGuns.MOD_ID, "textures/vehicle/tg_ejected.png"));
		}

		private String builtInName() {
			return switch (this.type.toLowerCase(Locale.ROOT)) {
				case "magazine" -> "tg_magazine";
				case "pistol" -> "tg_casing_pistol";
				case "shotgun" -> "tg_casing_shotgun";
				case "large" -> "tg_casing_large";
				default -> "tg_casing_rifle";
			};
		}
	}
}
