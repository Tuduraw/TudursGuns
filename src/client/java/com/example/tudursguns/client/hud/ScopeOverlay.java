package com.example.tudursguns.client.hud;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.client.AimController;
import com.example.tudursvehiclemod.client.render.AddonTextureLoader;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import java.util.Locale;

/** What the player sees through a scope: the scope texture as a centred square (the spyglass's by
 * default), black around it, and the current magnification. Drawn before the crosshair, which is
 * hidden meanwhile - the scope has its own reticle. */
public final class ScopeOverlay {

	private ScopeOverlay() {
	}

	private static final double RANGEFINDER_MAX = 1024.0;

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR,
				Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, "scope_overlay"), ScopeOverlay::render);
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, crosshair -> (context, tickCounter) -> {
			if (!AimController.isScoped()) {
				crosshair.render(context, tickCounter);
			}
		});
	}

	/** Distance from the eye to the block under the centre of the view (as far as the client has the
	 * world loaded - its render distance). */
	private static Component rangeText(Minecraft client, DeltaTracker tickCounter) {
		Entity camera = client.getCameraEntity();
		if (camera == null) {
			return Component.literal("---");
		}
		float tickProgress = tickCounter.getGameTimeDeltaPartialTick(true);
		HitResult hit = camera.pick(RANGEFINDER_MAX, tickProgress, false);
		if (hit.getType() == HitResult.Type.MISS) {
			return Component.translatable("hud.tudursguns.range", "---");
		}
		double distance = hit.getLocation().distanceTo(camera.getEyePosition(tickProgress));
		return Component.translatable("hud.tudursguns.range", String.format(Locale.ROOT, "%.0f", distance));
	}

	private static void render(GuiGraphics context, DeltaTracker tickCounter) {
		if (!AimController.isScoped()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		int width = context.guiWidth();
		int height = context.guiHeight();
		int size = Math.min(width, height);
		int x = (width - size) / 2;
		int y = (height - size) / 2;
		// An overlay shipped in an addon folder (textures/vehicle/...) is loaded by Tudur's Vehicle Mod on
		// request; resource-pack textures are unaffected.
		Identifier overlay = AimController.scopeOverlay();
		AddonTextureLoader.requestLoad(overlay);
		context.blit(RenderPipelines.GUI_TEXTURED, overlay, x, y, 0.0f, 0.0f, size, size, size, size);
		int black = 0xFF000000;
		context.fill(0, 0, width, y, black);
		context.fill(0, y + size, width, height, black);
		context.fill(0, y, x, y + size, black);
		context.fill(x + size, y, width, y + size, black);

		String magnification = String.format(Locale.ROOT, "x%.1f", AimController.magnification());
		context.drawString(client.font, Component.literal(magnification),
				x + size - client.font.width(magnification) - 8, y + size - 16, 0xFFFFFFFF);
		if (AimController.hasRangefinder()) {
			Component range = rangeText(client, tickCounter);
			int textX = (width - client.font.width(range)) / 2;
			context.drawString(client.font, range, textX, height / 2 + 12, 0xFFFF6040);
			context.fill(width / 2 - 4, height / 2, width / 2 + 5, height / 2 + 1, 0xFFFF6040);
			context.fill(width / 2, height / 2 - 4, width / 2 + 1, height / 2 + 5, 0xFFFF6040);
		}
	}
}
