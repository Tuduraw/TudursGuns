package com.example.tudursguns.handheld;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.item.ItemDisplayContext;

import java.util.Locale;
import java.util.Map;

/** Codec pieces shared by the definition formats. */
public final class DefinitionCodecs {

	private DefinitionCodecs() {
	}

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
			DefinitionCodecs.DISPLAY;
}
