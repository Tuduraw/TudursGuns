package com.example.tudursguns.registry;

import com.example.tudursguns.TudursGuns;
import com.mojang.serialization.Codec;
import com.example.tudursguns.weapon.WeaponAnimationEvents;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.Map;
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
	/** World time until which the weapon can't fire again (its Delay after a shot) - lets the client
	 * know not to raise it on use meanwhile. */
	public static ComponentType<Long> COOLDOWN_UNTIL;
	/** Selected ModeNum mode index (0-based). */
	public static ComponentType<Integer> MODE;
	/** Attachments fitted to a weapon: slot name -> attachment definition id. */
	public static ComponentType<Map<String, Identifier>> ATTACHMENTS;
	/** Which AttachmentDefinition an attachment item is (data/<namespace>/attachment/<path>.json). */
	public static ComponentType<Identifier> ATTACHMENT;
	/** Which ThrowableDefinition a throwable item is (data/<namespace>/throwable/<path>.json). */
	public static ComponentType<Identifier> THROWABLE;
	/** Which MineDefinition a mine item is (data/<namespace>/mine/<path>.json). */
	public static ComponentType<Identifier> MINE;
	/** Which ArmorDefinition an armor item is (data/<namespace>/armor/<path>.json). */
	public static ComponentType<Identifier> ARMOR;
	/** Which EquipmentDefinition an equipment item is (data/<namespace>/equipment/<path>.json). */
	public static ComponentType<Identifier> EQUIPMENT;
	/** Underbarrel launcher: rounds loaded in it, and whether it's the one selected to fire. */
	public static ComponentType<Integer> ALT_AMMO;
	public static ComponentType<Boolean> ALT_SELECTED;
	/** Animation: when each event last happened to this weapon (see WeaponAnimationEvents). */
	public static ComponentType<Map<String, WeaponAnimationEvents.Occurrence>> ANIM_EVENTS;
	/** Animation: the definition's counters (a revolver's cylinder position, ...). */
	public static ComponentType<Map<String, Integer>> ANIM_COUNTERS;

	public static void register() {
		WEAPON = ModComponents.<Identifier>register("weapon", builder -> builder.codec(Identifier.CODEC).packetCodec(Identifier.PACKET_CODEC));
		AMMO = ModComponents.<Integer>register("ammo", builder -> builder.codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT).skipsHandAnimation());
		RELOAD_UNTIL = ModComponents.<Long>register("reload_until", builder -> builder.codec(Codec.LONG).packetCodec(PacketCodecs.VAR_LONG).skipsHandAnimation());
		COOLDOWN_UNTIL = ModComponents.<Long>register("cooldown_until", builder -> builder.codec(Codec.LONG).packetCodec(PacketCodecs.VAR_LONG).skipsHandAnimation());
		MODE = ModComponents.<Integer>register("mode", builder -> builder.codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT).skipsHandAnimation());
		Codec<Map<String, Identifier>> attachmentsCodec = Codec.unboundedMap(Codec.STRING, Identifier.CODEC);
		ATTACHMENTS = ModComponents.<Map<String, Identifier>>register("attachments",
				builder -> builder.codec(attachmentsCodec).packetCodec(PacketCodecs.codec(attachmentsCodec)));
		ATTACHMENT = ModComponents.<Identifier>register("attachment", builder -> builder.codec(Identifier.CODEC).packetCodec(Identifier.PACKET_CODEC));
		THROWABLE = ModComponents.<Identifier>register("throwable", builder -> builder.codec(Identifier.CODEC).packetCodec(Identifier.PACKET_CODEC));
		MINE = ModComponents.<Identifier>register("mine", builder -> builder.codec(Identifier.CODEC).packetCodec(Identifier.PACKET_CODEC));
		ARMOR = ModComponents.<Identifier>register("armor", builder -> builder.codec(Identifier.CODEC).packetCodec(Identifier.PACKET_CODEC));
		EQUIPMENT = ModComponents.<Identifier>register("equipment", builder -> builder.codec(Identifier.CODEC).packetCodec(Identifier.PACKET_CODEC));
		ALT_AMMO = ModComponents.<Integer>register("alt_ammo", builder -> builder.codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT).skipsHandAnimation());
		ALT_SELECTED = ModComponents.<Boolean>register("alt_selected", builder -> builder.codec(Codec.BOOL).packetCodec(PacketCodecs.BOOLEAN).skipsHandAnimation());
		Codec<Map<String, WeaponAnimationEvents.Occurrence>> eventsCodec = Codec.unboundedMap(Codec.STRING, WeaponAnimationEvents.Occurrence.CODEC);
		ANIM_EVENTS = ModComponents.<Map<String, WeaponAnimationEvents.Occurrence>>register("anim_events",
				builder -> builder.codec(eventsCodec).packetCodec(PacketCodecs.codec(eventsCodec)).skipsHandAnimation());
		Codec<Map<String, Integer>> countersCodec = Codec.unboundedMap(Codec.STRING, Codec.INT);
		ANIM_COUNTERS = ModComponents.<Map<String, Integer>>register("anim_counters",
				builder -> builder.codec(countersCodec).packetCodec(PacketCodecs.codec(countersCodec)).skipsHandAnimation());
	}

	private static <T> ComponentType<T> register(String path, UnaryOperator<ComponentType.Builder<T>> builder) {
		return Registry.register(Registries.DATA_COMPONENT_TYPE, Identifier.of(TudursGuns.MOD_ID, path),
				builder.apply(ComponentType.builder()).build());
	}
}
