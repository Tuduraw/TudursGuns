package com.example.tudursguns.registry;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.block.AmmoBoxBlock;
import com.example.tudursguns.block.ScreenBlock;
import com.example.tudursguns.block.SoldierPostBlock;
import com.example.tudursguns.screen.GunCraftingScreenHandler;
import com.example.tudursguns.screen.WeaponWorkbenchScreenHandler;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

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
				Component.translatable("container.tudursguns.weapon_workbench"), WeaponWorkbenchScreenHandler::new), metal());
		WEAPON_WORKBENCH_ITEM = blockItem("weapon_workbench", WEAPON_WORKBENCH);
		GUN_CRAFTING_TABLE = block("gun_crafting_table", settings -> new ScreenBlock(settings,
				Component.translatable("container.tudursguns.gun_crafting_table"), GunCraftingScreenHandler::new), metal());
		GUN_CRAFTING_TABLE_ITEM = blockItem("gun_crafting_table", GUN_CRAFTING_TABLE);
		AMMO_BOX = block("ammo_box", AmmoBoxBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f));
		AMMO_BOX_ITEM = blockItem("ammo_box", AMMO_BOX);
		SOLDIER_POST = block("soldier_post", SoldierPostBlock::new, metal());
		SOLDIER_POST_ITEM = blockItem("soldier_post", SOLDIER_POST);
	}

	private static BlockBehaviour.Properties metal() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f).requiresCorrectToolForDrops();
	}

	private static Block block(String path, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties settings) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, path));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(settings.setId(key)));
	}

	private static Item blockItem(String path, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, path));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}
}
