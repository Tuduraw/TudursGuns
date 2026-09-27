package com.example.tudursguns.client.hud;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.entity.SmokeCloudEntity;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

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
				Identifier.of(TudursGuns.MOD_ID, "vision_overlay"), VisionOverlay::render);
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

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		if (client.player != null && client.options.getPerspective().isFirstPerson()
				&& com.example.tudursguns.armor.ArmorEffects.hasNightVision(client.player)) {
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
	private static float smokeAtCamera(MinecraftClient client) {
		if (client.world == null || client.gameRenderer == null) {
			return 0f;
		}
		Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
		float strongest = 0f;
		for (SmokeCloudEntity cloud : client.world.getEntitiesByClass(SmokeCloudEntity.class,
				new Box(camera, camera).expand(24.0), cloud -> !cloud.isSignal())) {
			float radius = cloud.currentRadius();
			if (radius <= 0.1f) {
				continue;
			}
			Vec3d center = cloud.getEntityPos().add(0, radius * 0.4, 0);
			double depth = 1.0 - camera.distanceTo(center) / radius;
			if (depth > 0) {
				strongest = Math.max(strongest, (float) Math.min(1.0, 0.75 + depth) * cloud.density());
			}
		}
		return strongest;
	}
}
