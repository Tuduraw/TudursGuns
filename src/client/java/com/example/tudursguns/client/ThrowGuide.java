package com.example.tudursguns.client;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.handheld.ThrowableDefinition;
import com.example.tudursguns.handheld.WeaponSummary;
import com.example.tudursguns.item.ThrowableItem;
import com.example.tudursguns.registry.ModComponents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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
	private static List<Vec3> arc = List.of();
	private static Vec3 impact;
	private static float ringRadius;

	public static void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, "throw_fuse"), ThrowGuide::renderFuse);
	}

	private static ItemStack heldThrowable(Player player) {
		if (player.isUsingItem()) {
			ItemStack active = player.getUseItem();
			if (active.getItem() instanceof ThrowableItem && active.has(ModComponents.THROWABLE)) {
				return active;
			}
		}
		return null;
	}

	public static void tick(Minecraft client) {
		tickCounter++;
		Player player = client.player;
		ItemStack stack = player == null || client.level == null || player.getVehicle() != null ? null : heldThrowable(player);
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
		try (var scope = client.collectPerTickGizmos()) {
			for (int i = 1; i < arc.size(); i++) {
				if (i % 2 == 1) {
					Gizmos.line(arc.get(i - 1), arc.get(i), ARC_COLOR, 2.0f);
				}
			}
			if (impact != null) {
				Gizmos.circle(impact.add(0, 0.05, 0), ringRadius, GizmoStyle.stroke(RING_COLOR));
			}
		}
	}

	private static void simulate(Minecraft client, Player player, ThrowableDefinition def, double gravity) {
		boolean underhand = AimController.isAimKeyHeldRaw(client);
		Vec3 look = player.getViewVector(1.0f);
		Vec3 direction = underhand
				? Vec3.directionFromRotation(Mth.clamp(player.getXRot() + 20f, -90f, 90f), player.getYRot())
				: look;
		Vec3 velocity = direction.scale(underhand ? def.underhandVelocity() : def.throwVelocity()).add(player.getDeltaMovement());
		Vec3 position = player.getEyePosition().add(look.scale(0.4)).add(0, -0.1, 0);
		List<Vec3> points = new ArrayList<>();
		points.add(position);
		Vec3 hitPos = null;
		for (int tick = 0; tick < MAX_TICKS; tick++) {
			velocity = velocity.add(0, -gravity, 0);
			Vec3 next = position.add(velocity);
			BlockHitResult hit = client.level.clip(new ClipContext(position, next,
					ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
			if (hit.getType() != HitResult.Type.MISS) {
				hitPos = hit.getLocation();
				points.add(hitPos);
				break;
			}
			points.add(next);
			position = next;
			velocity = velocity.scale(0.99);
		}
		arc = points;
		impact = hitPos;
		ringRadius = def.effect().radius() > 0f ? def.effect().radius() : 2.0f;
	}

	/** Seconds left before a cooking throwable goes off in hand, and whether it'll be lobbed underhand. */
	private static void renderFuse(GuiGraphics context, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		ItemStack stack = player == null || player.getVehicle() != null ? null : heldThrowable(player);
		ThrowableDefinition def = stack == null ? null : ModDefinitions.THROWABLES.getAny(stack.get(ModComponents.THROWABLE));
		if (def == null) {
			return;
		}
		int x = context.guiWidth() / 2 + 12;
		int y = context.guiHeight() / 2 + 8;
		if (def.cooks()) {
			int left = Math.max(0, def.fuseTicks() - player.getTicksUsingItem());
			float seconds = left / 20f;
			int color = seconds < 1.5f ? 0xFFFF4040 : 0xFFFFFFFF;
			Component text = Component.translatable("hud.tudursguns.fuse", String.format(Locale.ROOT, "%.1f", seconds));
			context.drawString(client.font, text, x, y, color);
			y += client.font.lineHeight + 2;
		}
		if (AimController.isAimKeyHeldRaw(client)) {
			context.drawString(client.font, Component.translatable("hud.tudursguns.underhand"), x, y, 0xFFC0C0C0);
		}
	}
}
