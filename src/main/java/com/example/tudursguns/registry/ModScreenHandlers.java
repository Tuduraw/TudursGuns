package com.example.tudursguns.registry;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.screen.GunCraftingScreenHandler;
import com.example.tudursguns.screen.SoldierPostScreenHandler;
import com.example.tudursguns.screen.WeaponWorkbenchScreenHandler;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;

public final class ModScreenHandlers {

	private ModScreenHandlers() {
	}

	public static MenuType<WeaponWorkbenchScreenHandler> WEAPON_WORKBENCH;
	public static MenuType<GunCraftingScreenHandler> GUN_CRAFTING;
	public static MenuType<SoldierPostScreenHandler> SOLDIER_POST;

	public static void register() {
		WEAPON_WORKBENCH = Registry.register(BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, "weapon_workbench"),
				new MenuType<>(WeaponWorkbenchScreenHandler::new, FeatureFlagSet.of()));
		GUN_CRAFTING = Registry.register(BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, "gun_crafting_table"),
				new MenuType<>(GunCraftingScreenHandler::new, FeatureFlagSet.of()));
		SOLDIER_POST = Registry.register(BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, "soldier_post"),
				new MenuType<>(SoldierPostScreenHandler::new, FeatureFlagSet.of()));
	}
}
