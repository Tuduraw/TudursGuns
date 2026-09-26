package com.example.tudursguns.client;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.client.hud.HandheldHud;
import com.example.tudursguns.client.hud.ScopeOverlay;
import com.example.tudursguns.client.render.ObjAttachmentModelRenderer;
import com.example.tudursguns.client.screen.WeaponWorkbenchScreen;
import com.example.tudursguns.handheld.AttachmentDefinition;
import com.example.tudursguns.client.render.ObjHandheldModelRenderer;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.HandheldDefinitions;
import com.example.tudursguns.item.HandheldWeaponItem;
import com.example.tudursguns.network.LockStatePayload;
import com.example.tudursguns.network.PlayerAimPayload;
import com.example.tudursguns.network.SyncAttachmentDefinitionsPayload;
import com.example.tudursguns.registry.ModScreenHandlers;
import com.example.tudursguns.network.ReloadRequestPayload;
import com.example.tudursguns.network.SwitchModeRequestPayload;
import com.example.tudursguns.network.SyncHandheldDefinitionsPayload;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.item.model.special.SpecialModelTypes;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;

public class TudursGunsClient implements ClientModInitializer {

	private static KeyBinding reloadKey;
	private static KeyBinding switchModeKey;

	@Override
	public void onInitializeClient() {
		TudursGunsClientConfig.load();
		// F3+T also re-reads config/tudursguns-client.json, for adjusting offsets without restarting.
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.of(TudursGuns.MOD_ID, "client_config");
			}

			@Override
			public void reload(ResourceManager manager) {
				TudursGunsClientConfig.load();
			}
		});

		SpecialModelTypes.ID_MAPPER.put(ObjHandheldModelRenderer.TYPE_ID, ObjHandheldModelRenderer.Unbaked.CODEC);
		SpecialModelTypes.ID_MAPPER.put(ObjAttachmentModelRenderer.TYPE_ID, ObjAttachmentModelRenderer.Unbaked.CODEC);
		HandledScreens.register(ModScreenHandlers.WEAPON_WORKBENCH, WeaponWorkbenchScreen::new);

		ClientPlayNetworking.registerGlobalReceiver(SyncAttachmentDefinitionsPayload.ID, (payload, context) ->
				context.client().execute(() -> HandheldDefinitions.setClientAttachments(decodeAttachments(payload))));
		ClientPlayNetworking.registerGlobalReceiver(PlayerAimPayload.ID, (payload, context) ->
				context.client().execute(() -> AimController.setRemoteAimKey(payload.entityId(), payload.held())));

		ClientPlayNetworking.registerGlobalReceiver(SyncHandheldDefinitionsPayload.ID, (payload, context) ->
				context.client().execute(() -> HandheldDefinitions.setClient(decode(payload))));
		ClientPlayNetworking.registerGlobalReceiver(LockStatePayload.ID, (payload, context) ->
				context.client().execute(() -> ClientLockState.set(payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			HandheldDefinitions.setClient(Map.of());
			HandheldDefinitions.setClientAttachments(Map.of());
			ClientLockState.set(LockStatePayload.NONE);
			AimController.reset();
		});

		// Defaults chosen to stay clear of both vanilla and Tudur's Vehicle Mod bindings
		// (R is its weapon switch, and several of its keys work on foot too).
		KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(TudursGuns.MOD_ID, "handheld"));
		reloadKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursguns.reload", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, category));
		switchModeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursguns.switch_mode", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_I, category));
		// Left Alt is also Tudur's Vehicle Mod's free-look key, but that only matters while riding,
		// and weapons can't be aimed while riding - AimController reads the key directly for this reason.
		AimController.setAimKey(KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursguns.aim", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, category)));

		ClientTickEvents.END_CLIENT_TICK.register(TudursGunsClient::onEndTick);

		HandheldHud.register();
		ScopeOverlay.register();
	}

	private static void onEndTick(MinecraftClient client) {
		AimController.tick(client);
		boolean reload = false;
		while (reloadKey.wasPressed()) {
			reload = true;
		}
		boolean switchMode = false;
		while (switchModeKey.wasPressed()) {
			switchMode = true;
		}
		PlayerEntity player = client.player;
		if (player == null || player.getVehicle() != null || !isHoldingWeapon(player)) {
			return;
		}
		if (reload) {
			ClientPlayNetworking.send(ReloadRequestPayload.INSTANCE);
		}
		if (switchMode) {
			ClientPlayNetworking.send(SwitchModeRequestPayload.INSTANCE);
		}
	}

	private static boolean isHoldingWeapon(PlayerEntity player) {
		for (Hand hand : Hand.values()) {
			if (player.getStackInHand(hand).getItem() instanceof HandheldWeaponItem) {
				return true;
			}
		}
		return false;
	}

	private static Map<Identifier, AttachmentDefinition> decodeAttachments(SyncAttachmentDefinitionsPayload payload) {
		Map<Identifier, AttachmentDefinition> received = new LinkedHashMap<>();
		for (SyncAttachmentDefinitionsPayload.Entry entry : payload.entries()) {
			try {
				AttachmentDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(entry.definitionJson()))
						.resultOrPartial(error -> TudursGuns.LOGGER.error("Failed to decode attachment definition '{}': {}", entry.id(), error))
						.ifPresent(def -> received.put(entry.id(), def));
			} catch (Exception e) {
				TudursGuns.LOGGER.error("Failed to decode attachment definition '{}'", entry.id(), e);
			}
		}
		return received;
	}

	private static Map<Identifier, HandheldDefinitions.ClientEntry> decode(SyncHandheldDefinitionsPayload payload) {
		Map<Identifier, HandheldDefinitions.ClientEntry> received = new LinkedHashMap<>();
		for (SyncHandheldDefinitionsPayload.Entry entry : payload.entries()) {
			try {
				HandheldDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(entry.definitionJson()))
						.resultOrPartial(error -> TudursGuns.LOGGER.error("Failed to decode handheld definition '{}': {}", entry.id(), error))
						.ifPresent(def -> received.put(entry.id(), new HandheldDefinitions.ClientEntry(def, entry.weapon())));
			} catch (Exception e) {
				TudursGuns.LOGGER.error("Failed to decode handheld definition '{}'", entry.id(), e);
			}
		}
		return received;
	}
}
