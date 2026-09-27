package com.example.tudursguns.handheld;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Optional;

/** One piece of armor (data/<namespace>/armor/<name>.json; keys in the README). It is worn through
 * vanilla's equippable component (see ArmorEffects.applyComponents) and looks like a vanilla 2D
 * equipment asset, an OBJ model, or both. OBJ armor faces +Z. */
public record ArmorDefinition(
		EquipmentSlot slot,
		Optional<String> displayName,
		float armor,
		float toughness,
		float knockbackResistance,
		float movementSpeed,
		int durability,
		Protection protection,
		Optional<Identifier> equipmentAsset,
		Optional<Identifier> itemModel,
		Optional<Identifier> model,
		Optional<Identifier> texture,
		Map<ItemDisplayContext, HandheldDefinition.DisplayTransform> display,
		HandheldDefinition.DisplayTransform worn,
		Effects effects
) {

	public static final Codec<ArmorDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			EquipmentSlot.CODEC.fieldOf("slot").forGetter(ArmorDefinition::slot),
			Codec.STRING.optionalFieldOf("display_name").forGetter(ArmorDefinition::displayName),
			Codec.floatRange(0f, 100f).optionalFieldOf("armor", 0f).forGetter(ArmorDefinition::armor),
			Codec.floatRange(0f, 100f).optionalFieldOf("toughness", 0f).forGetter(ArmorDefinition::toughness),
			Codec.floatRange(0f, 1f).optionalFieldOf("knockback_resistance", 0f).forGetter(ArmorDefinition::knockbackResistance),
			Codec.floatRange(-1f, 10f).optionalFieldOf("movement_speed", 0f).forGetter(ArmorDefinition::movementSpeed),
			Codec.intRange(0, 100000).optionalFieldOf("durability", 0).forGetter(ArmorDefinition::durability),
			Protection.CODEC.optionalFieldOf("protection", Protection.NONE).forGetter(ArmorDefinition::protection),
			Identifier.CODEC.optionalFieldOf("equipment_asset").forGetter(ArmorDefinition::equipmentAsset),
			Identifier.CODEC.optionalFieldOf("item_model").forGetter(ArmorDefinition::itemModel),
			Identifier.CODEC.optionalFieldOf("model").forGetter(ArmorDefinition::model),
			Identifier.CODEC.optionalFieldOf("texture").forGetter(ArmorDefinition::texture),
			DefinitionCodecs.DISPLAY.forGetter(ArmorDefinition::display),
			HandheldDefinition.DisplayTransform.CODEC.optionalFieldOf("worn", HandheldDefinition.DisplayTransform.IDENTITY).forGetter(ArmorDefinition::worn),
			Effects.CODEC.optionalFieldOf("effects", Effects.NONE).forGetter(ArmorDefinition::effects)
	).apply(instance, ArmorDefinition::new));

	public record Protection(float ballistic, float blast, float headshot) {

		public static final Protection NONE = new Protection(0f, 0f, 0f);

		public static final Codec<Protection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.floatRange(0f, 1f).optionalFieldOf("ballistic", 0f).forGetter(Protection::ballistic),
				Codec.floatRange(0f, 1f).optionalFieldOf("blast", 0f).forGetter(Protection::blast),
				Codec.floatRange(0f, 1f).optionalFieldOf("headshot", 0f).forGetter(Protection::headshot)
		).apply(instance, Protection::new));
	}

	public record Effects(boolean nightVision, boolean gasProtection, float detectionMultiplier) {

		public static final Effects NONE = new Effects(false, false, 1f);

		public static final Codec<Effects> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.BOOL.optionalFieldOf("night_vision", false).forGetter(Effects::nightVision),
				Codec.BOOL.optionalFieldOf("gas_protection", false).forGetter(Effects::gasProtection),
				Codec.floatRange(0f, 10f).optionalFieldOf("detection_multiplier", 1f).forGetter(Effects::detectionMultiplier)
		).apply(instance, Effects::new));
	}
}
