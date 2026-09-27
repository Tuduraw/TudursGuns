package com.example.tudursguns.handheld;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Util;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Codec pieces shared by the definition formats. */
public final class DefinitionCodecs {

	private DefinitionCodecs() {
	}

	/** [x, y, z] as a JSON array of three numbers. */
	public static final Codec<Vector3f> VECTOR_3F = Codec.FLOAT.listOf().comapFlatMap(
			list -> Util.decodeFixedLengthList(list, 3).map(xyz -> new Vector3f(xyz.get(0), xyz.get(1), xyz.get(2))),
			vector -> List.of(vector.x(), vector.y(), vector.z()));

	/** An enum written as its lower-case name; anything unknown reads as fallback. */
	public static <E extends Enum<E>> Codec<E> lenientEnum(Class<E> type, E fallback) {
		return Codec.STRING.xmap(name -> {
			try {
				return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return fallback;
			}
		}, value -> value.name().toLowerCase(Locale.ROOT));
	}

	/** The "display" key: extra transforms per display context (none by default). */
	public static final MapCodec<Map<ItemDisplayContext, HandheldDefinition.DisplayTransform>> DISPLAY =
			Codec.unboundedMap(ItemDisplayContext.CODEC, HandheldDefinition.DisplayTransform.CODEC).optionalFieldOf("display", Map.of());
}
