package com.example.tudursguns.client.hud;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.armor.ArmorEffects;
import com.example.tudursguns.entity.SmokeCloudEntity;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Screen effects that block the view:
 * - smoke: while the camera is inside a smoke cloud the view is filled with its grey, thicker towards
 *   the middle of the cloud, so nothing can be seen through it from inside;
 * - flash: a white-out from a flash grenade (FlashPayload), fading out;
 * - night vision goggles: a faint green tint while worn (the brightening itself is vanilla's Night
 *   Vision effect, given by the server). */
public final class VisionOverlay {

	private VisionOverlay() {
	}

	private static float flashIntensity;
	private static int flashTicksLeft;
	private static int flashTicksTotal;

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR,
				Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, "vision_overlay"), VisionOverlay::render);
	}

	public static void flash(float intensity, int durationTicks) {
		if (intensity >= currentFlash()) {
			flashIntensity = intensity;
			flashTicksTotal = Math.max(1, durationTicks);
			flashTicksLeft = flashTicksTotal;
		}
	}

	public static void tick() {
		if (flashTicksLeft > 0) {
			flashTicksLeft--;
		}
	}

	private static float currentFlash() {
		return flashTicksLeft <= 0 ? 0f : flashIntensity * flashTicksLeft / (float) flashTicksTotal;
	}

	private static void render(GuiGraphics context, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		int width = context.guiWidth();
		int height = context.guiHeight();
		if (client.player != null && client.options.getCameraType().isFirstPerson()
				&& ArmorEffects.hasNightVision(client.player)) {
			context.fill(0, 0, width, height, 0x2A20FF50);
		}
		float smoke = smokeAtCamera(client);
		if (smoke > 0f) {
			int alpha = Math.round(Math.min(0.97f, smoke) * 255f);
			context.fill(0, 0, width, height, (alpha << 24) | 0x8A8A8A);
		}
		float flash = currentFlash();
		if (flash > 0f) {
			// Stays fully white for most of the effect, then fades.
			float shown = Math.min(1f, flash * 1.6f);
			context.fill(0, 0, width, height, (Math.round(shown * 255f) << 24) | 0xFFFFFF);
		}
	}

	/** 0-1: how deep the camera is inside any smoke cloud. */
	private static float smokeAtCamera(Minecraft client) {
		if (client.level == null || client.gameRenderer == null) {
			return 0f;
		}
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		float strongest = 0f;
		for (SmokeCloudEntity cloud : client.level.getEntitiesOfClass(SmokeCloudEntity.class,
				new AABB(camera, camera).inflate(24.0), cloud -> !cloud.isSignal())) {
			float radius = cloud.currentRadius();
			if (radius <= 0.1f) {
				continue;
			}
			Vec3 center = cloud.position().add(0, radius * 0.4, 0);
			double depth = 1.0 - camera.distanceTo(center) / radius;
			if (depth > 0) {
				strongest = Math.max(strongest, (float) Math.min(1.0, 0.75 + depth) * cloud.density());
			}
		}
		return strongest;
	}
}
