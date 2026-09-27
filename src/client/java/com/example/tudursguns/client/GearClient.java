package com.example.tudursguns.client;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.entity.MineEntity;
import com.example.tudursguns.handheld.EquipmentDefinition;
import com.example.tudursguns.item.EquipmentItem;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

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
	private static final Identifier BEEP = Identifier.ofVanilla("block.note_block.bit");

	private static int beepCooldown;
	private static double nearestMine = -1;

	public static void register() {
		HudElementRegistry.addLast(Identifier.of(TudursGuns.MOD_ID, "gear_hud"), GearClient::renderHud);
	}

	private static EquipmentDefinition held(PlayerEntity player, EquipmentDefinition.Type type) {
		for (Hand hand : Hand.values()) {
			EquipmentDefinition def = EquipmentItem.definition(player.getStackInHand(hand));
			if (def != null && def.type() == type) {
				return def;
			}
		}
		return null;
	}

	public static void tick(MinecraftClient client) {
		PlayerEntity player = client.player;
		nearestMine = -1;
		if (player == null || client.world == null) {
			return;
		}
		EquipmentDefinition detector = held(player, EquipmentDefinition.Type.MINE_DETECTOR);
		if (detector != null) {
			detect(client, player, detector);
		}
		ItemStack active = player.isUsingItem() ? player.getActiveItem() : ItemStack.EMPTY;
		EquipmentDefinition activeDef = EquipmentItem.definition(active);
		if (activeDef != null && activeDef.type() == EquipmentDefinition.Type.LASER_DESIGNATOR) {
			beam(client, player, activeDef);
		}
	}

	private static void detect(MinecraftClient client, PlayerEntity player, EquipmentDefinition detector) {
		double range = detector.effectiveRange();
		Vec3d eye = player.getEyePos();
		try (var scope = client.newGizmoScope()) {
			for (MineEntity mine : client.world.getEntitiesByClass(MineEntity.class, player.getBoundingBox().expand(range),
					mine -> mine.squaredDistanceTo(player) <= range * range)) {
				GizmoDrawing.box(mine.getBoundingBox().expand(0.1), DrawStyle.stroked(OUTLINE_COLOR)).ignoreOcclusion();
				double distance = mine.getEntityPos().distanceTo(eye);
				if (nearestMine < 0 || distance < nearestMine) {
					nearestMine = distance;
				}
			}
		}
		if (nearestMine >= 0 && --beepCooldown <= 0) {
			beepCooldown = (int) Math.max(2, Math.min(30, nearestMine * 2.5));
			SoundEvent beep = Registries.SOUND_EVENT.get(BEEP);
			if (beep != null) {
				player.playSound(beep, 0.5f, 1.6f);
			}
		}
	}

	private static void beam(MinecraftClient client, PlayerEntity player, EquipmentDefinition def) {
		HitResult hit = player.raycast(def.effectiveRange(), client.getRenderTickCounter().getTickProgress(true), true);
		if (hit.getType() == HitResult.Type.MISS) {
			return;
		}
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0f);
		double yawRad = Math.toRadians(player.getYaw());
		Vec3d start = eye.add(look.multiply(0.5)).add(-Math.cos(yawRad) * 0.2, -0.2, -Math.sin(yawRad) * 0.2);
		try (var scope = client.newGizmoScope()) {
			GizmoDrawing.line(start, hit.getPos(), BEAM_COLOR, 1.5f);
		}
	}

	private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden || held(client.player, EquipmentDefinition.Type.MINE_DETECTOR) == null) {
			return;
		}
		Text text = nearestMine < 0 ? Text.translatable("hud.tudursguns.detector.none")
				: Text.translatable("hud.tudursguns.detector.nearest", String.format(Locale.ROOT, "%.1f", nearestMine));
		context.drawTextWithShadow(client.textRenderer, text, context.getScaledWindowWidth() / 2 + 12,
				context.getScaledWindowHeight() / 2 - 16, nearestMine < 0 ? 0xFF80FF80 : 0xFFFFC020);
	}
}
