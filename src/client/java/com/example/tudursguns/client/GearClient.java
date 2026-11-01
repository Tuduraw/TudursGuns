package com.example.tudursguns.client;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.entity.MineEntity;
import com.example.tudursguns.handheld.EquipmentDefinition;
import com.example.tudursguns.item.EquipmentItem;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.Locale;

/** Client side of the support equipment that only shows something to its holder:
 * - mine detector: outlines placed mines within range (through walls), beeps faster the nearer the
 *   closest one, and shows its distance;
 * - laser designator: the beam, from the holder to the spot (everyone sees the spot itself). */
public final class GearClient {

	private GearClient() {
	}

	private static final int OUTLINE_COLOR = 0xFFFFC020;
	private static final int BEAM_COLOR = 0xB0FF2020;
	private static final Identifier BEEP = Identifier.withDefaultNamespace("block.note_block.bit");

	private static int beepCooldown;
	private static double nearestMine = -1;

	public static void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, "gear_hud"), GearClient::renderHud);
	}

	private static EquipmentDefinition held(Player player, EquipmentDefinition.Type type) {
		for (InteractionHand hand : InteractionHand.values()) {
			EquipmentDefinition def = EquipmentItem.definition(player.getItemInHand(hand));
			if (def != null && def.type() == type) {
				return def;
			}
		}
		return null;
	}

	public static void tick(Minecraft client) {
		Player player = client.player;
		nearestMine = -1;
		if (player == null || client.level == null) {
			return;
		}
		EquipmentDefinition detector = held(player, EquipmentDefinition.Type.MINE_DETECTOR);
		if (detector != null) {
			detect(client, player, detector);
		}
		ItemStack active = player.isUsingItem() ? player.getUseItem() : ItemStack.EMPTY;
		EquipmentDefinition activeDef = EquipmentItem.definition(active);
		if (activeDef != null && activeDef.type() == EquipmentDefinition.Type.LASER_DESIGNATOR) {
			beam(client, player, activeDef);
		}
	}

	private static void detect(Minecraft client, Player player, EquipmentDefinition detector) {
		double range = detector.effectiveRange();
		Vec3 eye = player.getEyePosition();
		try (var scope = client.collectPerTickGizmos()) {
			for (MineEntity mine : client.level.getEntitiesOfClass(MineEntity.class, player.getBoundingBox().inflate(range),
					mine -> mine.distanceToSqr(player) <= range * range)) {
				Gizmos.cuboid(mine.getBoundingBox().inflate(0.1), GizmoStyle.stroke(OUTLINE_COLOR)).setAlwaysOnTop();
				double distance = mine.position().distanceTo(eye);
				if (nearestMine < 0 || distance < nearestMine) {
					nearestMine = distance;
				}
			}
		}
		if (nearestMine >= 0 && --beepCooldown <= 0) {
			beepCooldown = (int) Math.max(2, Math.min(30, nearestMine * 2.5));
			SoundEvent beep = BuiltInRegistries.SOUND_EVENT.getValue(BEEP);
			if (beep != null) {
				player.playSound(beep, 0.5f, 1.6f);
			}
		}
	}

	private static void beam(Minecraft client, Player player, EquipmentDefinition def) {
		HitResult hit = player.pick(def.effectiveRange(), client.getDeltaTracker().getGameTimeDeltaPartialTick(true), true);
		if (hit.getType() == HitResult.Type.MISS) {
			return;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		double yawRad = Math.toRadians(player.getYRot());
		Vec3 start = eye.add(look.scale(0.5)).add(-Math.cos(yawRad) * 0.2, -0.2, -Math.sin(yawRad) * 0.2);
		try (var scope = client.collectPerTickGizmos()) {
			Gizmos.line(start, hit.getLocation(), BEAM_COLOR, 1.5f);
		}
	}

	private static void renderHud(GuiGraphics context, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui || held(client.player, EquipmentDefinition.Type.MINE_DETECTOR) == null) {
			return;
		}
		Component text = nearestMine < 0 ? Component.translatable("hud.tudursguns.detector.none")
				: Component.translatable("hud.tudursguns.detector.nearest", String.format(Locale.ROOT, "%.1f", nearestMine));
		context.drawString(client.font, text, context.guiWidth() / 2 + 12,
				context.guiHeight() / 2 - 16, nearestMine < 0 ? 0xFF80FF80 : 0xFFFFC020);
	}
}
