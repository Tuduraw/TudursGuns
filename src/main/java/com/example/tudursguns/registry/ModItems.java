package com.example.tudursguns.registry;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.item.AmmoItem;
import com.example.tudursguns.item.ArmorItem;
import com.example.tudursguns.item.AttachmentItem;
import com.example.tudursguns.item.EquipmentItem;
import com.example.tudursguns.item.MineItem;
import com.example.tudursguns.item.HandheldWeaponItem;
import com.example.tudursguns.item.ThrowableItem;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.component.ComponentType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModItems {

	private ModItems() {
	}

	public static Item HANDHELD_WEAPON;
	public static Item ATTACHMENT;
	public static Item THROWABLE;
	public static Item MINE;
	public static Item ARMOR;
	public static Item EQUIPMENT;
	public static Item AMMO;

	/** Holding use (right click) fires, so the item is "in use" while the trigger is held. By default
	 * that slows the player to 20% speed and stops sprinting, like drawing a bow - this keeps full
	 * speed and sprinting instead, through vanilla's minecraft:use_effects component. */
	private static final String USE_EFFECTS_JSON = "{\"can_sprint\": true, \"speed_multiplier\": 1.0}";

	public static void register() {
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(TudursGuns.MOD_ID, "handheld_weapon"));
		Item.Settings settings = new Item.Settings().registryKey(key).maxCount(1);
		settings = withUseEffects(settings);
		HANDHELD_WEAPON = Registry.register(Registries.ITEM, key, new HandheldWeaponItem(settings));

		RegistryKey<Item> attachmentKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(TudursGuns.MOD_ID, "attachment"));
		ATTACHMENT = Registry.register(Registries.ITEM, attachmentKey,
				new AttachmentItem(new Item.Settings().registryKey(attachmentKey).maxCount(16)));

		RegistryKey<Item> throwableKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(TudursGuns.MOD_ID, "throwable"));
		THROWABLE = Registry.register(Registries.ITEM, throwableKey,
				new ThrowableItem(withUseEffects(new Item.Settings().registryKey(throwableKey).maxCount(16))));

		RegistryKey<Item> mineKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(TudursGuns.MOD_ID, "mine"));
		MINE = Registry.register(Registries.ITEM, mineKey, new MineItem(new Item.Settings().registryKey(mineKey).maxCount(8)));

		// Stack size, durability and the equippable component come from each definition (set on the stack).
		RegistryKey<Item> armorKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(TudursGuns.MOD_ID, "armor"));
		ARMOR = Registry.register(Registries.ITEM, armorKey, new ArmorItem(new Item.Settings().registryKey(armorKey).maxCount(1)));

		RegistryKey<Item> equipmentKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(TudursGuns.MOD_ID, "equipment"));
		EQUIPMENT = Registry.register(Registries.ITEM, equipmentKey,
				new EquipmentItem(new Item.Settings().registryKey(equipmentKey).maxCount(1)));

		RegistryKey<Item> ammoKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(TudursGuns.MOD_ID, "ammo"));
		AMMO = Registry.register(Registries.ITEM, ammoKey, new AmmoItem(new Item.Settings().registryKey(ammoKey).maxCount(16)));
	}

	/** Looked up by id and built through its own codec rather than by class: the component is new in
	 * 1.21.11 and has no Yarn name yet. If that fails the item simply keeps vanilla's slowdown. */
	private static Item.Settings withUseEffects(Item.Settings settings) {
		ComponentType<?> type = Registries.DATA_COMPONENT_TYPE.get(Identifier.ofVanilla("use_effects"));
		if (type == null) {
			TudursGuns.LOGGER.warn("minecraft:use_effects not found - handheld weapons will slow the player while firing");
			return settings;
		}
		return applyFromJson(settings, type);
	}

	private static <T> Item.Settings applyFromJson(Item.Settings settings, ComponentType<T> type) {
		return type.getCodecOrThrow().parse(JsonOps.INSTANCE, JsonParser.parseString(USE_EFFECTS_JSON))
				.resultOrPartial(error -> TudursGuns.LOGGER.warn("Couldn't build minecraft:use_effects ({}) - handheld weapons will slow the player while firing", error))
				.map(value -> settings.component(type, value))
				.orElse(settings);
	}
}
