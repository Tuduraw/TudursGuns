package com.example.tudursguns.registry;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.armor.ArmorEffects;
import com.example.tudursguns.handheld.ArmorDefinition;
import com.example.tudursguns.handheld.EquipmentDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.handheld.WeaponSummary;
import com.example.tudursguns.item.AmmoItem;
import com.example.tudursguns.item.ArmorItem;
import com.example.tudursguns.item.AttachmentItem;
import com.example.tudursguns.item.EquipmentItem;
import com.example.tudursguns.item.HandheldWeaponItem;
import com.example.tudursguns.item.MineItem;
import com.example.tudursguns.item.ThrowableItem;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.UseEffects;

/** The item types. Each is one item for every definition of its kind: which definition a stack is
 * comes from a component (tudursguns:weapon, tudursguns:mine, ...), since items can't be registered
 * after startup. */
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

	/** Holding use fires / pulls the pin, so the item is "in use" while it's held down - without this,
	 * that slows the player like drawing a bow and stops sprinting. */
	private static final UseEffects FULL_SPEED_USE =
			new UseEffects(true, UseEffects.DEFAULT.interactVibrations(), 1.0f);

	public static void register() {
		HANDHELD_WEAPON = register("handheld_weapon", HandheldWeaponItem::new,
				new Item.Properties().stacksTo(1).component(DataComponents.USE_EFFECTS, FULL_SPEED_USE));
		ATTACHMENT = register("attachment", AttachmentItem::new, new Item.Properties().stacksTo(16));
		THROWABLE = register("throwable", ThrowableItem::new,
				new Item.Properties().stacksTo(16).component(DataComponents.USE_EFFECTS, FULL_SPEED_USE));
		MINE = register("mine", MineItem::new, new Item.Properties().stacksTo(8));
		// Armor and equipment get their stack size, durability and equippable component from each
		// definition (set on the stack).
		ARMOR = register("armor", ArmorItem::new, new Item.Properties().stacksTo(1));
		EQUIPMENT = register("equipment", EquipmentItem::new, new Item.Properties().stacksTo(1));
		AMMO = register("ammo", AmmoItem::new, new Item.Properties().stacksTo(16));
	}

	private static Item register(String path, Function<Item.Properties, Item> factory, Item.Properties settings) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, path));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(settings.setId(key)));
	}

	// ---------------------------------------------------------------- stacks

	private static ItemStack defined(Item item, DataComponentType<Identifier> component, Identifier id) {
		ItemStack stack = new ItemStack(item);
		stack.set(component, id);
		return stack;
	}

	/** A weapon, loaded (when its weapon file is known on this side). */
	public static ItemStack weaponStack(Identifier id) {
		ItemStack stack = defined(HANDHELD_WEAPON, ModComponents.WEAPON, id);
		WeaponSummary weapon = ModDefinitions.HANDHELD.weapon(id);
		if (weapon != null && weapon.magazineSize() > 0) {
			stack.set(ModComponents.AMMO, weapon.magazineSize());
		}
		return stack;
	}

	public static ItemStack attachmentStack(Identifier id) {
		return defined(ATTACHMENT, ModComponents.ATTACHMENT, id);
	}

	public static ItemStack throwableStack(Identifier id) {
		return defined(THROWABLE, ModComponents.THROWABLE, id);
	}

	public static ItemStack mineStack(Identifier id) {
		return defined(MINE, ModComponents.MINE, id);
	}

	public static ItemStack ammoStack(Identifier id) {
		return defined(AMMO, ModComponents.AMMO_TYPE, id);
	}

	/** Armor with its components filled in, so it can be worn straight away. */
	public static ItemStack armorStack(Identifier id) {
		ItemStack stack = defined(ARMOR, ModComponents.ARMOR, id);
		ArmorDefinition def = ModDefinitions.ARMOR.getAny(id);
		if (def != null) {
			ArmorEffects.applyComponents(stack, def);
		}
		return stack;
	}

	public static ItemStack equipmentStack(Identifier id) {
		ItemStack stack = defined(EQUIPMENT, ModComponents.EQUIPMENT, id);
		EquipmentDefinition def = ModDefinitions.EQUIPMENT.getAny(id);
		if (def != null) {
			EquipmentItem.applyComponents(stack, def);
		}
		return stack;
	}
}
