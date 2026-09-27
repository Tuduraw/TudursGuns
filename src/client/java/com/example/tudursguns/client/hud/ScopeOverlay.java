package com.example.tudursguns.client.hud;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.client.AimController;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.Identifier;

/** What the player sees through a scope: the scope texture as a centred square (the spyglass's by
 * default), black around it, and the current magnification. Drawn before the crosshair, which is
 * hidden meanwhile - the scope has its own reticle. */
public final class ScopeOverlay {

	private ScopeOverlay() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR,
				Identifier.of(TudursGuns.MOD_ID, "scope_overlay"), ScopeOverlay::render);
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, crosshair -> (context, tickCounter) -> {
			if (!AimController.isScoped()) {
				crosshair.render(context, tickCounter);
			}
		});
	}

	private static final double RANGEFINDER_MAX = 1024.0;

	/** Distance from the eye to the block under the centre of the view (as far as the client has the
	 * world loaded - its render distance). */
	private static Text rangeText(MinecraftClient client, RenderTickCounter tickCounter) {
		Entity camera = client.getCameraEntity();
		if (camera == null) {
			return Text.literal("---");
		}
		float tickProgress = tickCounter.getTickProgress(true);
		HitResult hit = camera.raycast(RANGEFINDER_MAX, tickProgress, false);
		if (hit.getType() == HitResult.Type.MISS) {
			return Text.translatable("hud.tudursguns.range", "---");
		}
		double distance = hit.getPos().distanceTo(camera.getCameraPosVec(tickProgress));
		return Text.translatable("hud.tudursguns.range", String.format(java.util.Locale.ROOT, "%.0f", distance));
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		if (!AimController.isScoped()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		int size = Math.min(width, height);
		int x = (width - size) / 2;
		int y = (height - size) / 2;
		context.drawTexture(RenderPipelines.GUI_TEXTURED, AimController.scopeOverlay(), x, y, 0.0f, 0.0f, size, size, size, size);
		int black = 0xFF000000;
		context.fill(0, 0, width, y, black);
		context.fill(0, y + size, width, height, black);
		context.fill(0, y, x, y + size, black);
		context.fill(x + size, y, width, y + size, black);

		String magnification = String.format(java.util.Locale.ROOT, "x%.1f", AimController.magnification());
		context.drawTextWithShadow(client.textRenderer, Text.literal(magnification),
				x + size - client.textRenderer.getWidth(magnification) - 8, y + size - 16, 0xFFFFFFFF);
		if (AimController.hasRangefinder()) {
			Text range = rangeText(client, tickCounter);
			int textX = (width - client.textRenderer.getWidth(range)) / 2;
			context.drawTextWithShadow(client.textRenderer, range, textX, height / 2 + 12, 0xFFFF6040);
			context.fill(width / 2 - 4, height / 2, width / 2 + 5, height / 2 + 1, 0xFFFF6040);
			context.fill(width / 2, height / 2 - 4, width / 2 + 1, height / 2 + 5, 0xFFFF6040);
		}
	}
}
