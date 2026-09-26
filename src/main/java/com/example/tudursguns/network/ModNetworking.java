package com.example.tudursguns.network;

import com.example.tudursguns.handheld.AttachmentDefinition;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.HandheldDefinitions;
import com.example.tudursguns.handheld.WeaponSummary;
import com.example.tudursguns.item.HandheldWeaponItem;
import com.example.tudursguns.weapon.HandheldCombat;
import com.example.tudursvehiclemod.asset.WeaponStats;
import com.example.tudursvehiclemod.asset.WeaponStatsLoader;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
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
		PayloadTypeRegistry.playS2C().register(SyncAttachmentDefinitionsPayload.ID, SyncAttachmentDefinitionsPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AimKeyPayload.ID, AimKeyPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PlayerAimPayload.ID, PlayerAimPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(AimKeyPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					ServerPlayerEntity player = context.player();
					if (HandheldCombat.setAimKeyHeld(player, payload.held())) {
						PlayerAimPayload broadcast = new PlayerAimPayload(player.getId(), payload.held());
						for (ServerPlayerEntity tracking : PlayerLookup.tracking(player)) {
							ServerPlayNetworking.send(tracking, broadcast);
						}
					}
				}));
		// A player who starts seeing someone already aiming needs to be told.
		EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) -> {
			if (trackedEntity instanceof ServerPlayerEntity tracked && HandheldCombat.isAimKeyHeld(tracked)) {
				ServerPlayNetworking.send(player, new PlayerAimPayload(tracked.getId(), true));
			}
		});

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

	public static SyncAttachmentDefinitionsPayload buildAttachmentSyncPayload() {
		List<SyncAttachmentDefinitionsPayload.Entry> entries = new ArrayList<>();
		for (Map.Entry<Identifier, AttachmentDefinition> entry : HandheldDefinitions.serverAttachments().entrySet()) {
			AttachmentDefinition.CODEC.encodeStart(JsonOps.INSTANCE, entry.getValue()).result().ifPresent(json ->
					entries.add(new SyncAttachmentDefinitionsPayload.Entry(entry.getKey(), json.toString())));
		}
		return new SyncAttachmentDefinitionsPayload(entries);
	}

	/** Attachments first: the weapon sync is what refreshes the client's weapon-dependent state. */
	public static void syncDefinitions(ServerPlayerEntity player) {
		ServerPlayNetworking.send(player, buildAttachmentSyncPayload());
		ServerPlayNetworking.send(player, buildSyncPayload());
	}

	public static void syncDefinitionsToAll(MinecraftServer server) {
		SyncAttachmentDefinitionsPayload attachments = buildAttachmentSyncPayload();
		SyncHandheldDefinitionsPayload weapons = buildSyncPayload();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(player, attachments);
			ServerPlayNetworking.send(player, weapons);
		}
	}
}
