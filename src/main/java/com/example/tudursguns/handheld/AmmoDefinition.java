package com.example.tudursguns.handheld;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Optional;

/** A kind of ammunition item (a magazine, a clip, a box of shells), read from
 * data/<namespace>/ammo/<name>.json. A weapon whose definition names it ("ammo") reloads from these
 * items instead of a plain item: each one gives rounds rounds.
 *
 * Looks: an OBJ model (model/texture/display), or a flat icon (a PNG), or both (the icon is used in
 * inventories). */
public record AmmoDefinition(
		Optional<String> displayName,
		int rounds,
		int maxStack,
		Optional<Identifier> model,
		Optional<Identifier> texture,
		Map<ItemDisplayContext, HandheldDefinition.DisplayTransform> display,
		Optional<Identifier> icon
) {

	public static final Codec<AmmoDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.optionalFieldOf("display_name").forGetter(AmmoDefinition::displayName),
			Codec.intRange(1, 1_000_000).optionalFieldOf("rounds", 1).forGetter(AmmoDefinition::rounds),
			Codec.intRange(1, 99).optionalFieldOf("max_stack", 16).forGetter(AmmoDefinition::maxStack),
			Identifier.CODEC.optionalFieldOf("model").forGetter(AmmoDefinition::model),
			Identifier.CODEC.optionalFieldOf("texture").forGetter(AmmoDefinition::texture),
			DefinitionCodecs.DISPLAY.forGetter(AmmoDefinition::display),
			Identifier.CODEC.optionalFieldOf("icon").forGetter(AmmoDefinition::icon)
	).apply(instance, AmmoDefinition::new));
}
