package com.example.tudursguns.registry;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.block.AmmoBoxBlock;
import com.example.tudursguns.block.ScreenBlock;
import com.example.tudursguns.block.SoldierPostBlock;
import com.example.tudursguns.screen.GunCraftingScreenHandler;
import com.example.tudursguns.screen.WeaponWorkbenchScreenHandler;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public final class ModBlocks {

	private ModBlocks() {
	}

	public static Block WEAPON_WORKBENCH;
	public static Item WEAPON_WORKBENCH_ITEM;
	public static Block GUN_CRAFTING_TABLE;
	public static Item GUN_CRAFTING_TABLE_ITEM;
	public static Block AMMO_BOX;
	public static Item AMMO_BOX_ITEM;
	public static Block SOLDIER_POST;
	public static Item SOLDIER_POST_ITEM;

	public static void register() {
		WEAPON_WORKBENCH = block("weapon_workbench", settings -> new ScreenBlock(settings,
				Text.translatable("container.tudursguns.weapon_workbench"), WeaponWorkbenchScreenHandler::new), metal());
		WEAPON_WORKBENCH_ITEM = blockItem("weapon_workbench", WEAPON_WORKBENCH);
		GUN_CRAFTING_TABLE = block("gun_crafting_table", settings -> new ScreenBlock(settings,
				Text.translatable("container.tudursguns.gun_crafting_table"), GunCraftingScreenHandler::new), metal());
		GUN_CRAFTING_TABLE_ITEM = blockItem("gun_crafting_table", GUN_CRAFTING_TABLE);
		AMMO_BOX = block("ammo_box", AmmoBoxBlock::new, AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN).strength(2.5f));
		AMMO_BOX_ITEM = blockItem("ammo_box", AMMO_BOX);
		SOLDIER_POST = block("soldier_post", SoldierPostBlock::new, metal());
		SOLDIER_POST_ITEM = blockItem("soldier_post", SOLDIER_POST);
	}

	private static AbstractBlock.Settings metal() {
		return AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY).strength(3.5f).requiresTool();
	}

	private static Block block(String path, Function<AbstractBlock.Settings, Block> factory, AbstractBlock.Settings settings) {
		RegistryKey<Block> key = RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(TudursGuns.MOD_ID, path));
		return Registry.register(Registries.BLOCK, key, factory.apply(settings.registryKey(key)));
	}

	private static Item blockItem(String path, Block block) {
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(TudursGuns.MOD_ID, path));
		return Registry.register(Registries.ITEM, key, new BlockItem(block, new Item.Settings().registryKey(key).useBlockPrefixedTranslationKey()));
	}
}
