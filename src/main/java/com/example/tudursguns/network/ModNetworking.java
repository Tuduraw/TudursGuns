package com.example.tudursguns.network;

import com.example.tudursguns.handheld.DefinitionSet;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.item.HandheldWeaponItem;
import com.example.tudursguns.weapon.Firing;
import com.example.tudursguns.weapon.HandheldCombat;
import com.example.tudursvehiclemod.asset.WeaponStats;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;

public final class ModNetworking {

	private ModNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(LockStatePayload.ID, LockStatePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ReloadRequestPayload.ID, ReloadRequestPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SwitchModeRequestPayload.ID, SwitchModeRequestPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AimKeyPayload.ID, AimKeyPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PlayerAimPayload.ID, PlayerAimPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(FlashPayload.ID, FlashPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncDefinitionsPayload.ID, SyncDefinitionsPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(RecoilPayload.ID, RecoilPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SwitchUnderbarrelRequestPayload.ID, SwitchUnderbarrelRequestPayload.CODEC);

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

		ServerPlayNetworking.registerGlobalReceiver(SwitchUnderbarrelRequestPayload.ID, (payload, context) ->
				context.server().execute(() -> {
					ServerPlayerEntity player = context.player();
					HeldWeapon held = heldWeapon(player);
					if (held != null && !HandheldCombat.toggleUnderbarrel(player, held.stack(), held.base())) {
						player.sendMessage(net.minecraft.text.Text.translatable("message.tudursguns.underbarrel.none"), true);
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

	/** base: the weapon's own definition; definition/stats: what it fires right now (see Firing). */
	private record HeldWeapon(ItemStack stack, HandheldDefinition base, HandheldDefinition definition, WeaponStats stats) {
	}

	/** The weapon in the main hand, else the off hand. */
	private static HeldWeapon heldWeapon(ServerPlayerEntity player) {
		for (Hand hand : Hand.values()) {
			ItemStack stack = player.getStackInHand(hand);
			if (stack.getItem() instanceof HandheldWeaponItem) {
				HandheldDefinition def = HandheldWeaponItem.serverDefinition(stack);
				if (def != null) {
					Firing firing = Firing.of(stack, def);
					return new HeldWeapon(stack, def, firing.definition(), firing.stats());
				}
			}
		}
		return null;
	}

	/** Every definition kind, as the server has it now. Clients get these on join and after /reload
	 * (definitions and weapon files both reload with data packs). */
	private static List<SyncDefinitionsPayload> syncPayloads() {
		List<SyncDefinitionsPayload> payloads = new ArrayList<>();
		for (DefinitionSet<?> set : ModDefinitions.ALL) {
			payloads.add(set.buildPayload());
		}
		return payloads;
	}

	public static void syncDefinitions(ServerPlayerEntity player) {
		for (SyncDefinitionsPayload payload : syncPayloads()) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	public static void syncDefinitionsToAll(MinecraftServer server) {
		List<SyncDefinitionsPayload> payloads = syncPayloads();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			for (SyncDefinitionsPayload payload : payloads) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}
}
