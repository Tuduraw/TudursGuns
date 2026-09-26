package com.example.tudursguns.registry;

import com.example.tudursguns.TudursGuns;
import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.function.UnaryOperator;

/** Per-stack state of a handheld weapon.
 *
 * Everything that changes while the weapon is in use (loaded rounds, reload, mode) skips the
 * held-item re-equip animation - otherwise every shot would visibly lower and raise the weapon. */
public final class ModComponents {

	private ModComponents() {
	}

	/** Which HandheldDefinition this stack is (data/<namespace>/handheld/<path>.json). */
	public static ComponentType<Identifier> WEAPON;
	/** Rounds currently loaded. */
	public static ComponentType<Integer> AMMO;
	/** World time at which the reload in progress completes; absent when not reloading. */
	public static ComponentType<Long> RELOAD_UNTIL;
	/** Selected ModeNum mode index (0-based). */
	public static ComponentType<Integer> MODE;

	public static void register() {
		WEAPON = ModComponents.<Identifier>register("weapon", builder -> builder.codec(Identifier.CODEC).packetCodec(Identifier.PACKET_CODEC));
		AMMO = ModComponents.<Integer>register("ammo", builder -> builder.codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT).skipsHandAnimation());
		RELOAD_UNTIL = ModComponents.<Long>register("reload_until", builder -> builder.codec(Codec.LONG).packetCodec(PacketCodecs.VAR_LONG).skipsHandAnimation());
		MODE = ModComponents.<Integer>register("mode", builder -> builder.codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT).skipsHandAnimation());
	}

	private static <T> ComponentType<T> register(String path, UnaryOperator<ComponentType.Builder<T>> builder) {
		return Registry.register(Registries.DATA_COMPONENT_TYPE, Identifier.of(TudursGuns.MOD_ID, path),
				builder.apply(ComponentType.builder()).build());
	}
}
