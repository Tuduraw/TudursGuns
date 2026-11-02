package com.example.tudursguns.client;

import net.minecraft.client.model.HumanoidModel;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.client.hud.HandheldHud;
import com.example.tudursguns.client.hud.ScopeOverlay;
import com.example.tudursguns.client.hud.VisionOverlay;
import com.example.tudursguns.client.render.InvisibleEntityRenderer;
import com.example.tudursguns.client.render.MineEntityRenderer;
import com.example.tudursguns.client.render.ObjArmorFeatureRenderer;
import com.example.tudursguns.client.render.ObjDefinedItemRenderer;
import com.example.tudursguns.client.render.ObjHandheldModelRenderer;
import com.example.tudursguns.client.render.SoldierRenderer;
import com.example.tudursguns.client.screen.GunCraftingScreen;
import com.example.tudursguns.client.screen.SoldierPostScreen;
import com.example.tudursguns.client.screen.SoldierRouteScreen;
import com.example.tudursguns.client.screen.WeaponWorkbenchScreen;
import com.example.tudursguns.handheld.DefinitionSet;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.network.FlashPayload;
import com.example.tudursguns.network.LockStatePayload;
import com.example.tudursguns.network.PlayerAimPayload;
import com.example.tudursguns.network.RecoilPayload;
import com.example.tudursguns.network.ReloadRequestPayload;
import com.example.tudursguns.network.SoldierRoutePayload;
import com.example.tudursguns.network.SwitchModeRequestPayload;
import com.example.tudursguns.network.SwitchUnderbarrelRequestPayload;
import com.example.tudursguns.network.SyncDefinitionsPayload;
import com.example.tudursguns.registry.ModEntityTypes;
import com.example.tudursguns.registry.ModScreenHandlers;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

public class TudursGunsClient implements ClientModInitializer {

	private static KeyMapping reloadKey;
	private static KeyMapping switchModeKey;
	private static KeyMapping underbarrelKey;

	@Override
	public void onInitializeClient() {
		TudursGunsClientConfig.load();
		// F3+T also re-reads config/tudursguns-client.json, for adjusting offsets without restarting.
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, "client_config");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				TudursGunsClientConfig.load();
			}
		});

		SpecialModelRenderers.ID_MAPPER.put(ObjHandheldModelRenderer.TYPE_ID, ObjHandheldModelRenderer.Unbaked.CODEC);
		EntityRendererRegistry.register(ModEntityTypes.SMOKE_CLOUD, InvisibleEntityRenderer::new);
		EntityRendererRegistry.register(ModEntityTypes.SMOKE_DECOY, InvisibleEntityRenderer::new);
		EntityRendererRegistry.register(ModEntityTypes.LASER_SPOT, InvisibleEntityRenderer::new);
		EntityRendererRegistry.register(ModEntityTypes.MINE, MineEntityRenderer::new);
		EntityRendererRegistry.register(ModEntityTypes.SOLDIER,
				context -> new SoldierRenderer<>(context, Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, "textures/entity/soldier.png")));
		EntityRendererRegistry.register(ModEntityTypes.ENEMY_SOLDIER,
				context -> new SoldierRenderer<>(context, Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, "textures/entity/enemy_soldier.png")));
		for (ObjDefinedItemRenderer.Kind kind : ObjDefinedItemRenderer.Kind.values()) {
			SpecialModelRenderers.ID_MAPPER.put(kind.typeId, kind.codec);
		}
		// OBJ armor on anything drawn with a humanoid model (players, armor stands, zombies, ...).
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer.getModel() instanceof HumanoidModel<?>) {
				registerArmorFeature(helper, entityRenderer);
			}
		});
		MenuScreens.register(ModScreenHandlers.WEAPON_WORKBENCH, WeaponWorkbenchScreen::new);
		MenuScreens.register(ModScreenHandlers.GUN_CRAFTING, GunCraftingScreen::new);
		MenuScreens.register(ModScreenHandlers.SOLDIER_POST, SoldierPostScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(SoldierRoutePayload.ID, (payload, context) ->
				context.client().execute(() -> context.client().setScreen(new SoldierRouteScreen(payload))));

		ClientPlayNetworking.registerGlobalReceiver(SyncDefinitionsPayload.ID, (payload, context) ->
				context.client().execute(() -> {
					DefinitionSet<?> set = ModDefinitions.byKind(payload.kind());
					if (set != null) {
						set.receive(payload);
					}
				}));
		ClientPlayNetworking.registerGlobalReceiver(RecoilPayload.ID, (payload, context) ->
				context.client().execute(() -> RecoilController.kick(payload.pitch(), payload.yaw())));
		ClientPlayNetworking.registerGlobalReceiver(FlashPayload.ID, (payload, context) ->
				context.client().execute(() -> VisionOverlay.flash(payload.intensity(), payload.durationTicks())));
		ClientPlayNetworking.registerGlobalReceiver(PlayerAimPayload.ID, (payload, context) ->
				context.client().execute(() -> AimController.setRemoteAimKey(payload.entityId(), payload.held())));

		ClientPlayNetworking.registerGlobalReceiver(LockStatePayload.ID, (payload, context) ->
				context.client().execute(() -> ClientLockState.set(payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			for (DefinitionSet<?> set : ModDefinitions.ALL) {
				set.clearClient();
			}
			ClientLockState.set(LockStatePayload.NONE);
			AimController.reset();
			RecoilController.reset();
		});

		// Defaults chosen to stay clear of both vanilla and Tudur's Vehicle Mod bindings
		// (R is its weapon switch, and several of its keys work on foot too).
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, "handheld"));
		reloadKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.tudursguns.reload", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, category));
		switchModeKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.tudursguns.switch_mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, category));
		// X is vanilla's "load hotbar" (creative only, unbound in survival use) - rebindable as usual.
		underbarrelKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.tudursguns.switch_underbarrel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, category));
		// Left Alt is also Tudur's Vehicle Mod's free-look key, but that only matters while riding,
		// and weapons can't be aimed while riding - AimController reads the key directly for this reason.
		AimController.setAimKey(KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.tudursguns.aim", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, category)));

		ClientTickEvents.END_CLIENT_TICK.register(TudursGunsClient::onEndTick);

		HandheldHud.register();
		ScopeOverlay.register();
		VisionOverlay.register();
		ThrowGuide.register();
		GearClient.register();
	}

	/** Raw types: the renderer's state and model types are only known to be biped ones at run time. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void registerArmorFeature(LivingEntityFeatureRendererRegistrationCallback.RegistrationHelper helper,
			LivingEntityRenderer<?, ?, ?> renderer) {
		helper.register(new ObjArmorFeatureRenderer(renderer));
	}

	private static void onEndTick(Minecraft client) {
		AimController.tick(client);
		ThrowGuide.tick(client);
		VisionOverlay.tick();
		GearClient.tick(client);
		RecoilController.tick(client);
		boolean reload = false;
		while (reloadKey.consumeClick()) {
			reload = true;
		}
		boolean switchMode = false;
		while (switchModeKey.consumeClick()) {
			switchMode = true;
		}
		boolean switchUnderbarrel = false;
		while (underbarrelKey.consumeClick()) {
			switchUnderbarrel = true;
		}
		Player player = client.player;
		if (player == null || player.getVehicle() != null || HandheldHud.heldWeapon(player) == null) {
			return;
		}
		if (reload) {
			ClientPlayNetworking.send(ReloadRequestPayload.INSTANCE);
		}
		if (switchMode) {
			ClientPlayNetworking.send(SwitchModeRequestPayload.INSTANCE);
		}
		if (switchUnderbarrel) {
			ClientPlayNetworking.send(SwitchUnderbarrelRequestPayload.INSTANCE);
		}
	}
}
