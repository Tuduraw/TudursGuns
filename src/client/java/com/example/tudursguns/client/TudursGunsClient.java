package com.example.tudursguns.client;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.client.hud.HandheldHud;
import com.example.tudursguns.client.render.ObjHandheldModelRenderer;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.HandheldDefinitions;
import com.example.tudursguns.item.HandheldWeaponItem;
import com.example.tudursguns.network.LockStatePayload;
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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.item.model.special.SpecialModelTypes;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
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
		SpecialModelTypes.ID_MAPPER.put(ObjHandheldModelRenderer.TYPE_ID, ObjHandheldModelRenderer.Unbaked.CODEC);

		ClientPlayNetworking.registerGlobalReceiver(SyncHandheldDefinitionsPayload.ID, (payload, context) ->
				context.client().execute(() -> HandheldDefinitions.setClient(decode(payload))));
		ClientPlayNetworking.registerGlobalReceiver(LockStatePayload.ID, (payload, context) ->
				context.client().execute(() -> ClientLockState.set(payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			HandheldDefinitions.setClient(Map.of());
			ClientLockState.set(LockStatePayload.NONE);
		});

		// Defaults chosen to stay clear of both vanilla and Tudur's Vehicle Mod bindings
		// (R is its weapon switch, and several of its keys work on foot too).
		KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(TudursGuns.MOD_ID, "handheld"));
		reloadKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursguns.reload", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, category));
		switchModeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursguns.switch_mode", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_I, category));

		ClientTickEvents.END_CLIENT_TICK.register(TudursGunsClient::onEndTick);

		HandheldHud.register();
	}

	private static void onEndTick(MinecraftClient client) {
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
