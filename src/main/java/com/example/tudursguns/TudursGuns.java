package com.example.tudursguns;

import com.example.tudursguns.armor.ArmorEffects;
import com.example.tudursguns.handheld.DefinitionSet;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.network.ModNetworking;
import com.example.tudursguns.registry.ModBlockEntities;
import com.example.tudursguns.registry.ModBlocks;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.registry.ModEntityTypes;
import com.example.tudursguns.registry.ModItemGroups;
import com.example.tudursguns.registry.ModItems;
import com.example.tudursguns.registry.ModScreenHandlers;
import com.example.tudursguns.weapon.EquipmentActions;
import com.example.tudursguns.weapon.HandheldCombat;
import com.example.tudursguns.weapon.HeldMovementEffects;
import com.example.tudursguns.weapon.ThrowableCombat;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
		TudursGunsConfig.load();
		ModComponents.register();
		ModItems.register();
		ModBlocks.register();
		ModBlockEntities.register();
		ModEntityTypes.register();
		ModScreenHandlers.register();
		ModItemGroups.register();
		ModNetworking.register();

		for (DefinitionSet<?> set : ModDefinitions.ALL) {
			ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(set.loader());
		}
		ServerTickEvents.END_WORLD_TICK.register(ArmorEffects::tickWorld);
		ServerTickEvents.END_WORLD_TICK.register(HeldMovementEffects::tickWorld);
		ServerTickEvents.END_SERVER_TICK.register(com.example.tudursguns.weapon.WeaponAnimationEvents::tick);
		ServerTickEvents.END_SERVER_TICK.register(com.example.tudursguns.weapon.FiringEffects::tick);
		// A thrown grenade's smoke/flash/fire happens where its projectile ends up - see ThrowableCombat.
		ServerEntityEvents.ENTITY_UNLOAD.register(ThrowableCombat::onProjectileRemoved);

		// Clients get every definition on join and again after /reload (definitions and weapon files
		// both reload with data packs).
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ModNetworking.syncDefinitions(handler.player));
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
			if (success) {
				ModNetworking.syncDefinitionsToAll(server);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			HandheldCombat.forget(handler.player.getUuid());
			ThrowableCombat.forget(handler.player.getUuid());
			EquipmentActions.forget(handler.player.getUuid());
			ArmorEffects.forget(handler.player.getUuid());
		});
	}
}
