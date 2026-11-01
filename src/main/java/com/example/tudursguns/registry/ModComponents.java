package com.example.tudursguns.registry;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.weapon.WeaponAnimationEvents;
import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

/** Per-stack state: which definition a stack is, and a weapon's loaded rounds, reload, mode and so on.
 *
 * Everything that changes while a weapon is in use skips the held-item re-equip animation -
 * otherwise every shot would visibly lower and raise the weapon. */
public final class ModComponents {

	private ModComponents() {
	}

	/** Which HandheldDefinition this stack is (data/<namespace>/handheld/<path>.json). */
	public static DataComponentType<Identifier> WEAPON;
	/** Rounds currently loaded. */
	public static DataComponentType<Integer> AMMO;
	/** World time at which the reload in progress completes; absent when not reloading. */
	public static DataComponentType<Long> RELOAD_UNTIL;
	/** World time until which the weapon can't fire again (its Delay after a shot) - lets the client
	 * know not to raise it on use meanwhile. */
	public static DataComponentType<Long> COOLDOWN_UNTIL;
	/** Selected ModeNum mode index (0-based). */
	public static DataComponentType<Integer> MODE;
	/** Attachments fitted to a weapon: slot name -> attachment definition id. */
	public static DataComponentType<Map<String, Identifier>> ATTACHMENTS;
	/** Which AttachmentDefinition an attachment item is (data/<namespace>/attachment/<path>.json). */
	public static DataComponentType<Identifier> ATTACHMENT;
	/** Which ThrowableDefinition a throwable item is (data/<namespace>/throwable/<path>.json). */
	public static DataComponentType<Identifier> THROWABLE;
	/** Which MineDefinition a mine item is (data/<namespace>/mine/<path>.json). */
	public static DataComponentType<Identifier> MINE;
	/** Which ArmorDefinition an armor item is (data/<namespace>/armor/<path>.json). */
	public static DataComponentType<Identifier> ARMOR;
	/** Which EquipmentDefinition an equipment item is (data/<namespace>/equipment/<path>.json). */
	public static DataComponentType<Identifier> EQUIPMENT;
	/** Which AmmoDefinition an ammo item is (data/<namespace>/ammo/<path>.json). */
	public static DataComponentType<Identifier> AMMO_TYPE;
	/** Underbarrel launcher: rounds loaded in it, and whether it's the one selected to fire. */
	public static DataComponentType<Integer> ALT_AMMO;
	public static DataComponentType<Boolean> ALT_SELECTED;
	/** Animation: when each event last happened to this weapon (see WeaponAnimationEvents). */
	public static DataComponentType<Map<String, WeaponAnimationEvents.Occurrence>> ANIM_EVENTS;
	/** Animation: the definition's counters (a revolver's cylinder position, ...). */
	public static DataComponentType<Map<String, Integer>> ANIM_COUNTERS;

	public static void register() {
		WEAPON = definitionId("weapon");
		AMMO = ModComponents.<Integer>register("ammo", builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).ignoreSwapAnimation());
		RELOAD_UNTIL = ModComponents.<Long>register("reload_until", builder -> builder.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG).ignoreSwapAnimation());
		COOLDOWN_UNTIL = ModComponents.<Long>register("cooldown_until", builder -> builder.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG).ignoreSwapAnimation());
		MODE = ModComponents.<Integer>register("mode", builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).ignoreSwapAnimation());
		Codec<Map<String, Identifier>> attachmentsCodec = Codec.unboundedMap(Codec.STRING, Identifier.CODEC);
		ATTACHMENTS = ModComponents.<Map<String, Identifier>>register("attachments",
				builder -> builder.persistent(attachmentsCodec).networkSynchronized(ByteBufCodecs.fromCodec(attachmentsCodec)));
		ATTACHMENT = definitionId("attachment");
		THROWABLE = definitionId("throwable");
		MINE = definitionId("mine");
		ARMOR = definitionId("armor");
		EQUIPMENT = definitionId("equipment");
		AMMO_TYPE = definitionId("ammo_type");
		ALT_AMMO = ModComponents.<Integer>register("alt_ammo", builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).ignoreSwapAnimation());
		ALT_SELECTED = ModComponents.<Boolean>register("alt_selected", builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).ignoreSwapAnimation());
		Codec<Map<String, WeaponAnimationEvents.Occurrence>> eventsCodec = Codec.unboundedMap(Codec.STRING, WeaponAnimationEvents.Occurrence.CODEC);
		ANIM_EVENTS = ModComponents.<Map<String, WeaponAnimationEvents.Occurrence>>register("anim_events",
				builder -> builder.persistent(eventsCodec).networkSynchronized(ByteBufCodecs.fromCodec(eventsCodec)).ignoreSwapAnimation());
		Codec<Map<String, Integer>> countersCodec = Codec.unboundedMap(Codec.STRING, Codec.INT);
		ANIM_COUNTERS = ModComponents.<Map<String, Integer>>register("anim_counters",
				builder -> builder.persistent(countersCodec).networkSynchronized(ByteBufCodecs.fromCodec(countersCodec)).ignoreSwapAnimation());
	}

	/** A stack's definition id (which weapon, mine, ... it is). */
	private static DataComponentType<Identifier> definitionId(String path) {
		return register(path, builder -> builder.persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC));
	}

	private static <T> DataComponentType<T> register(String path, UnaryOperator<DataComponentType.Builder<T>> builder) {
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, path),
				builder.apply(DataComponentType.builder()).build());
	}
}
