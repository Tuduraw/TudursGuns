package com.example.tudursguns.client;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.handheld.ThrowableDefinition;
import com.example.tudursguns.handheld.WeaponSummary;
import com.example.tudursguns.item.ThrowableItem;
import com.example.tudursguns.registry.ModComponents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** While a throwable is held ready to throw: the throw's arc and a ring where it will first land
 * (drawn in the world the same way Tudur's Vehicle Mod marks a bomb's impact point), plus the time
 * left on a cooking fuse. The arc follows the thrown projectile's own physics - gravity from its weapon
 * file, then movement, then 0.99 drag per tick - and ignores bounces after the first landing. */
public final class ThrowGuide {

	private ThrowGuide() {
	}

	private static final int ARC_COLOR = 0xC0FFFFFF;
	private static final int RING_COLOR = 0xFFFF5040;
	private static final int MAX_TICKS = 200;
	/** Recompute the arc every few ticks; the gizmos themselves are re-added every tick. */
	private static final int SIMULATION_INTERVAL_TICKS = 2;

	private static int tickCounter;
	private static List<Vec3d> arc = List.of();
	private static Vec3d impact;
	private static float ringRadius;

	public static void register() {
		HudElementRegistry.addLast(Identifier.of(TudursGuns.MOD_ID, "throw_fuse"), ThrowGuide::renderFuse);
	}

	private static ItemStack heldThrowable(PlayerEntity player) {
		if (player.isUsingItem()) {
			ItemStack active = player.getActiveItem();
			if (active.getItem() instanceof ThrowableItem && active.contains(ModComponents.THROWABLE)) {
				return active;
			}
		}
		return null;
	}

	public static void tick(MinecraftClient client) {
		tickCounter++;
		PlayerEntity player = client.player;
		ItemStack stack = player == null || client.world == null || player.getVehicle() != null ? null : heldThrowable(player);
		Identifier id = stack == null ? null : stack.get(ModComponents.THROWABLE);
		ThrowableDefinition def = ModDefinitions.THROWABLES.getAny(id);
		WeaponSummary weapon = ModDefinitions.THROWABLES.weapon(id);
		if (def == null || weapon == null) {
			arc = List.of();
			impact = null;
			return;
		}
		if (tickCounter % SIMULATION_INTERVAL_TICKS == 0) {
			simulate(client, player, def, weapon.gravity());
		}
		if (arc.size() < 2) {
			return;
		}
		try (var scope = client.newGizmoScope()) {
			for (int i = 1; i < arc.size(); i++) {
				if (i % 2 == 1) {
					GizmoDrawing.line(arc.get(i - 1), arc.get(i), ARC_COLOR, 2.0f);
				}
			}
			if (impact != null) {
				GizmoDrawing.circle(impact.add(0, 0.05, 0), ringRadius, DrawStyle.stroked(RING_COLOR));
			}
		}
	}

	private static void simulate(MinecraftClient client, PlayerEntity player, ThrowableDefinition def, double gravity) {
		boolean underhand = AimController.isAimKeyHeldRaw(client);
		Vec3d look = player.getRotationVec(1.0f);
		Vec3d direction = underhand
				? Vec3d.fromPolar(MathHelper.clamp(player.getPitch() + 20f, -90f, 90f), player.getYaw())
				: look;
		Vec3d velocity = direction.multiply(underhand ? def.underhandVelocity() : def.throwVelocity()).add(player.getVelocity());
		Vec3d position = player.getEyePos().add(look.multiply(0.4)).add(0, -0.1, 0);
		List<Vec3d> points = new ArrayList<>();
		points.add(position);
		Vec3d hitPos = null;
		for (int tick = 0; tick < MAX_TICKS; tick++) {
			velocity = velocity.add(0, -gravity, 0);
			Vec3d next = position.add(velocity);
			BlockHitResult hit = client.world.raycast(new RaycastContext(position, next,
					RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, player));
			if (hit.getType() != HitResult.Type.MISS) {
				hitPos = hit.getPos();
				points.add(hitPos);
				break;
			}
			points.add(next);
			position = next;
			velocity = velocity.multiply(0.99);
		}
		arc = points;
		impact = hitPos;
		ringRadius = def.effect().radius() > 0f ? def.effect().radius() : 2.0f;
	}

	/** Seconds left before a cooking throwable goes off in hand, and whether it'll be lobbed underhand. */
	private static void renderFuse(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		PlayerEntity player = client.player;
		ItemStack stack = player == null || player.getVehicle() != null ? null : heldThrowable(player);
		ThrowableDefinition def = stack == null ? null : ModDefinitions.THROWABLES.getAny(stack.get(ModComponents.THROWABLE));
		if (def == null) {
			return;
		}
		int x = context.getScaledWindowWidth() / 2 + 12;
		int y = context.getScaledWindowHeight() / 2 + 8;
		if (def.cooks()) {
			int left = Math.max(0, def.fuseTicks() - player.getItemUseTime());
			float seconds = left / 20f;
			int color = seconds < 1.5f ? 0xFFFF4040 : 0xFFFFFFFF;
			Text text = Text.translatable("hud.tudursguns.fuse", String.format(Locale.ROOT, "%.1f", seconds));
			context.drawTextWithShadow(client.textRenderer, text, x, y, color);
			y += client.textRenderer.fontHeight + 2;
		}
		if (AimController.isAimKeyHeldRaw(client)) {
			context.drawTextWithShadow(client.textRenderer, Text.translatable("hud.tudursguns.underhand"), x, y, 0xFFC0C0C0);
		}
	}
}
