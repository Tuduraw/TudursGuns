package com.example.tudursguns.network;

import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.HandheldDefinitions;
import com.example.tudursguns.handheld.WeaponSummary;
import com.example.tudursguns.item.HandheldWeaponItem;
import com.example.tudursguns.weapon.HandheldCombat;
import com.example.tudursvehiclemod.asset.WeaponStats;
import com.example.tudursvehiclemod.asset.WeaponStatsLoader;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ModNetworking {

	private ModNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(SyncHandheldDefinitionsPayload.ID, SyncHandheldDefinitionsPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(LockStatePayload.ID, LockStatePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ReloadRequestPayload.ID, ReloadRequestPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SwitchModeRequestPayload.ID, SwitchModeRequestPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ReloadRequestPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					ServerPlayerEntity player = context.player();
					HeldWeapon held = heldWeapon(player);
					if (held != null && player.getVehicle() == null) {
						HandheldCombat.startReload(player, held.stack(), held.definition(), held.stats(), true);
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(SwitchModeRequestPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					ServerPlayerEntity player = context.player();
					HeldWeapon held = heldWeapon(player);
					if (held != null) {
						HandheldCombat.cycleMode(player, held.stack(), held.stats());
					}
				}));
	}

	private record HeldWeapon(ItemStack stack, HandheldDefinition definition, WeaponStats stats) {
	}

	/** The weapon in the main hand, else the off hand. */
	private static HeldWeapon heldWeapon(ServerPlayerEntity player) {
		for (Hand hand : Hand.values()) {
			ItemStack stack = player.getStackInHand(hand);
			if (stack.getItem() instanceof HandheldWeaponItem) {
				HandheldDefinition def = HandheldWeaponItem.serverDefinition(stack);
				if (def != null) {
					return new HeldWeapon(stack, def, WeaponStatsLoader.get(def.weapon()));
				}
			}
		}
		return null;
	}

	/** Builds the definition sync from the server's current definitions and weapon files. */
	public static SyncHandheldDefinitionsPayload buildSyncPayload() {
		List<SyncHandheldDefinitionsPayload.Entry> entries = new ArrayList<>();
		for (Map.Entry<Identifier, HandheldDefinition> entry : HandheldDefinitions.server().entrySet()) {
			HandheldDefinition.CODEC.encodeStart(JsonOps.INSTANCE, entry.getValue()).result().ifPresent(json ->
					entries.add(new SyncHandheldDefinitionsPayload.Entry(entry.getKey(), json.toString(),
							WeaponSummary.of(WeaponStatsLoader.get(entry.getValue().weapon())))));
		}
		return new SyncHandheldDefinitionsPayload(entries);
	}

	public static void syncDefinitions(ServerPlayerEntity player) {
		ServerPlayNetworking.send(player, buildSyncPayload());
	}

	public static void syncDefinitionsToAll(MinecraftServer server) {
		SyncHandheldDefinitionsPayload payload = buildSyncPayload();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
