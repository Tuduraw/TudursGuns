package com.example.tudursguns;

import com.example.tudursguns.handheld.HandheldDefinitionLoader;
import com.example.tudursguns.network.ModNetworking;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.registry.ModItemGroups;
import com.example.tudursguns.registry.ModItems;
import com.example.tudursguns.weapon.HandheldCombat;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TudursGuns implements ModInitializer {

	public static final String MOD_ID = "tudursguns";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModComponents.register();
		ModItems.register();
		ModItemGroups.register();
		ModNetworking.register();

		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new HandheldDefinitionLoader());

		// Clients get every definition on join and again after /reload (definitions and weapon files
		// both reload with data packs).
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ModNetworking.syncDefinitions(handler.player));
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
			if (success) {
				ModNetworking.syncDefinitionsToAll(server);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HandheldCombat.forget(handler.player.getUuid()));
	}
}
