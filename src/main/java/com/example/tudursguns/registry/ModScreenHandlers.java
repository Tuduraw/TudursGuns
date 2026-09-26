package com.example.tudursguns.registry;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.screen.WeaponWorkbenchScreenHandler;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public final class ModScreenHandlers {

	private ModScreenHandlers() {
	}

	public static ScreenHandlerType<WeaponWorkbenchScreenHandler> WEAPON_WORKBENCH;

	public static void register() {
		WEAPON_WORKBENCH = Registry.register(Registries.SCREEN_HANDLER, Identifier.of(TudursGuns.MOD_ID, "weapon_workbench"),
				new ScreenHandlerType<>(WeaponWorkbenchScreenHandler::new, FeatureSet.empty()));
	}
}
