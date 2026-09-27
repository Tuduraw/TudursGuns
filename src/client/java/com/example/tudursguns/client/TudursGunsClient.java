package com.example.tudursguns.client;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.client.hud.HandheldHud;
import com.example.tudursguns.client.hud.ScopeOverlay;
import com.example.tudursguns.client.hud.VisionOverlay;
import com.example.tudursguns.client.render.InvisibleEntityRenderer;
import com.example.tudursguns.client.render.MineEntityRenderer;
import com.example.tudursguns.client.render.ObjArmorFeatureRenderer;
import com.example.tudursguns.client.render.ObjDefinedItemRenderer;
import com.example.tudursguns.client.render.ObjHandheldModelRenderer;
import com.example.tudursguns.client.screen.GunCraftingScreen;
import com.example.tudursguns.client.screen.WeaponWorkbenchScreen;
import com.example.tudursguns.handheld.DefinitionSet;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.item.HandheldWeaponItem;
import com.example.tudursguns.network.FlashPayload;
import com.example.tudursguns.network.LockStatePayload;
import com.example.tudursguns.network.PlayerAimPayload;
import com.example.tudursguns.network.RecoilPayload;
import com.example.tudursguns.network.ReloadRequestPayload;
import com.example.tudursguns.network.SwitchModeRequestPayload;
import com.example.tudursguns.network.SwitchUnderbarrelRequestPayload;
import com.example.tudursguns.network.SyncDefinitionsPayload;
import com.example.tudursguns.registry.ModEntityTypes;
import com.example.tudursguns.registry.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.item.model.special.SpecialModelTypes;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class TudursGunsClient implements ClientModInitializer {

	private static KeyBinding reloadKey;
	private static KeyBinding switchModeKey;
	private static KeyBinding underbarrelKey;

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
		EntityRendererRegistry.register(ModEntityTypes.SMOKE_CLOUD, InvisibleEntityRenderer::new);
		EntityRendererRegistry.register(ModEntityTypes.SMOKE_DECOY, InvisibleEntityRenderer::new);
		EntityRendererRegistry.register(ModEntityTypes.LASER_SPOT, InvisibleEntityRenderer::new);
		EntityRendererRegistry.register(ModEntityTypes.MINE, MineEntityRenderer::new);
		for (ObjDefinedItemRenderer.Kind kind : ObjDefinedItemRenderer.Kind.values()) {
			SpecialModelTypes.ID_MAPPER.put(kind.typeId, kind.codec);
		}
		// OBJ armor on anything drawn with a humanoid model (players, armor stands, zombies, ...).
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer.getModel() instanceof BipedEntityModel<?>) {
				registerArmorFeature(helper, entityRenderer);
			}
		});
		HandledScreens.register(ModScreenHandlers.WEAPON_WORKBENCH, WeaponWorkbenchScreen::new);
		HandledScreens.register(ModScreenHandlers.GUN_CRAFTING, GunCraftingScreen::new);

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
		KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(TudursGuns.MOD_ID, "handheld"));
		reloadKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursguns.reload", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, category));
		switchModeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursguns.switch_mode", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_I, category));
		// X is vanilla's "load hotbar" (creative only, unbound in survival use) - rebindable as usual.
		underbarrelKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursguns.switch_underbarrel", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X, category));
		// Left Alt is also Tudur's Vehicle Mod's free-look key, but that only matters while riding,
		// and weapons can't be aimed while riding - AimController reads the key directly for this reason.
		AimController.setAimKey(KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.tudursguns.aim", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, category)));

		ClientTickEvents.END_CLIENT_TICK.register(TudursGunsClient::onEndTick);

		HandheldHud.register();
		ScopeOverlay.register();
		VisionOverlay.register();
		ThrowGuide.register();
		GearClient.register();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void registerArmorFeature(LivingEntityFeatureRendererRegistrationCallback.RegistrationHelper helper,
			LivingEntityRenderer<?, ?, ?> renderer) {
		helper.register((FeatureRenderer) ObjArmorFeatureRenderer.create(renderer));
	}

	private static void onEndTick(MinecraftClient client) {
		AimController.tick(client);
		ThrowGuide.tick(client);
		VisionOverlay.tick();
		GearClient.tick(client);
		RecoilController.tick(client);
		boolean reload = false;
		while (reloadKey.wasPressed()) {
			reload = true;
		}
		boolean switchMode = false;
		while (switchModeKey.wasPressed()) {
			switchMode = true;
		}
		boolean switchUnderbarrel = false;
		while (underbarrelKey.wasPressed()) {
			switchUnderbarrel = true;
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
		if (switchUnderbarrel) {
			ClientPlayNetworking.send(SwitchUnderbarrelRequestPayload.INSTANCE);
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
}
