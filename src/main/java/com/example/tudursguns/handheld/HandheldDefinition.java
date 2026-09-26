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
		Optional<String> hud,
		Map<ItemDisplayContext, DisplayTransform> display
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
			Codec.STRING.optionalFieldOf("hud").forGetter(HandheldDefinition::hud),
			Codec.unboundedMap(ItemDisplayContext.CODEC, DisplayTransform.CODEC).optionalFieldOf("display", Map.of()).forGetter(HandheldDefinition::display)
	).apply(instance, HandheldDefinition::new));

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
