package com.example.tudursguns.registry;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.block.AmmoBoxBlock;
import com.example.tudursguns.block.GunCraftingTableBlock;
import com.example.tudursguns.block.WeaponWorkbenchBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModBlocks {

	private ModBlocks() {
	}

	public static Block WEAPON_WORKBENCH;
	public static Item WEAPON_WORKBENCH_ITEM;
	public static Block GUN_CRAFTING_TABLE;
	public static Item GUN_CRAFTING_TABLE_ITEM;
	public static Block AMMO_BOX;
	public static Item AMMO_BOX_ITEM;

	public static void register() {
		RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(TudursGuns.MOD_ID, "weapon_workbench"));
		WEAPON_WORKBENCH = Registry.register(Registries.BLOCK, blockKey,
				new WeaponWorkbenchBlock(AbstractBlock.Settings.create()
						.registryKey(blockKey)
						.mapColor(MapColor.IRON_GRAY)
						.strength(3.5f)
						.requiresTool()));

		RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(TudursGuns.MOD_ID, "weapon_workbench"));
		WEAPON_WORKBENCH_ITEM = Registry.register(Registries.ITEM, itemKey,
				new BlockItem(WEAPON_WORKBENCH, new Item.Settings().registryKey(itemKey).useBlockPrefixedTranslationKey()));

		RegistryKey<Block> craftingKey = RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(TudursGuns.MOD_ID, "gun_crafting_table"));
		GUN_CRAFTING_TABLE = Registry.register(Registries.BLOCK, craftingKey,
				new GunCraftingTableBlock(AbstractBlock.Settings.create()
						.registryKey(craftingKey)
						.mapColor(MapColor.IRON_GRAY)
						.strength(3.5f)
						.requiresTool()));
		RegistryKey<Item> craftingItemKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(TudursGuns.MOD_ID, "gun_crafting_table"));
		GUN_CRAFTING_TABLE_ITEM = Registry.register(Registries.ITEM, craftingItemKey,
				new BlockItem(GUN_CRAFTING_TABLE, new Item.Settings().registryKey(craftingItemKey).useBlockPrefixedTranslationKey()));

		RegistryKey<Block> ammoBoxKey = RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(TudursGuns.MOD_ID, "ammo_box"));
		AMMO_BOX = Registry.register(Registries.BLOCK, ammoBoxKey,
				new AmmoBoxBlock(AbstractBlock.Settings.create()
						.registryKey(ammoBoxKey)
						.mapColor(MapColor.OAK_TAN)
						.strength(2.5f)));
		RegistryKey<Item> ammoBoxItemKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(TudursGuns.MOD_ID, "ammo_box"));
		AMMO_BOX_ITEM = Registry.register(Registries.ITEM, ammoBoxItemKey,
				new BlockItem(AMMO_BOX, new Item.Settings().registryKey(ammoBoxItemKey).useBlockPrefixedTranslationKey()));
	}
}
