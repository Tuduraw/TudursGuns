package com.example.tudursguns.client.hud;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.client.ClientLockState;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.HandheldDefinitions;
import com.example.tudursguns.item.HandheldWeaponItem;
import com.example.tudursguns.network.LockStatePayload;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursvehiclemod.client.hud.HudExecutionContext;
import com.example.tudursvehiclemod.client.hud.HudScript;
import com.example.tudursvehiclemod.client.hud.HudScriptLoader;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Ammunition, reload and lock-on readout for the weapon in hand.
 *
 * A definition may name a HUD script ("hud") - the same MCHeli-style scripts vehicles use, run through
 * Tudur's Vehicle Mod's own script engine with the variables listed in buildVariables(). Without
 * one (or if the named script isn't found) a plain text readout is drawn next to the crosshair. */
public final class HandheldHud {

	private HandheldHud() {
	}

	public static void register() {
		HudElementRegistry.addLast(Identifier.of(TudursGuns.MOD_ID, "handheld_hud"), HandheldHud::render);
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		PlayerEntity player = client.player;
		if (player == null || client.world == null || player.getVehicle() != null || client.options.hudHidden) {
			return;
		}
		ItemStack stack = heldWeapon(player);
		if (stack == null) {
			return;
		}
		HandheldDefinitions.ClientEntry entry = HandheldDefinitions.getClient(stack.get(ModComponents.WEAPON));
		if (entry == null) {
			return;
		}

		Map<String, Double> variables = buildVariables(client, player, stack, entry);
		int centerX = context.getScaledWindowWidth() / 2;
		int centerY = context.getScaledWindowHeight() / 2;

		String scriptName = entry.definition().hud().orElse(null);
		Map<String, HudScript> scripts = HudScriptLoader.getScripts();
		if (scriptName != null && scripts.containsKey(scriptName)) {
			Map<String, String> stringVariables = new HashMap<>();
			stringVariables.put("weapon_name", stack.getName().getString());
			new HudExecutionContext(context, client, variables, stringVariables, centerX, centerY, scripts).run(scriptName);
			return;
		}
		drawDefault(context, client, stack, entry, variables, centerX, centerY);
	}

	private static ItemStack heldWeapon(PlayerEntity player) {
		for (Hand hand : Hand.values()) {
			ItemStack stack = player.getStackInHand(hand);
			if (stack.getItem() instanceof HandheldWeaponItem && stack.contains(ModComponents.WEAPON)) {
				return stack;
			}
		}
		return null;
	}

	/** Variables available to a handheld HUD script:
	 * ammo, max_ammo, reserve_ammo (-1 = unlimited), reloading (0/1), reload_progress (0-1),
	 * mode (1-based), mode_count, lock_tracking (0/1), locked (0/1), lock_progress (0-1). */
	private static Map<String, Double> buildVariables(MinecraftClient client, PlayerEntity player, ItemStack stack,
			HandheldDefinitions.ClientEntry entry) {
		Map<String, Double> variables = new HashMap<>();
		HandheldDefinition def = entry.definition();
		variables.put("ammo", (double) stack.getOrDefault(ModComponents.AMMO, 0));
		variables.put("max_ammo", (double) entry.weapon().magazineSize());
		variables.put("reserve_ammo", (double) reserveRounds(player, def));

		Long reloadUntil = stack.get(ModComponents.RELOAD_UNTIL);
		boolean reloading = reloadUntil != null;
		double reloadProgress = 0.0;
		if (reloading && entry.weapon().reloadTicks() > 0) {
			long remaining = reloadUntil - client.world.getTime();
			reloadProgress = Math.max(0.0, Math.min(1.0, 1.0 - remaining / (double) entry.weapon().reloadTicks()));
		}
		variables.put("reloading", reloading ? 1.0 : 0.0);
		variables.put("reload_progress", reloadProgress);

		variables.put("mode", (double) (stack.getOrDefault(ModComponents.MODE, 0) + 1));
		variables.put("mode_count", (double) entry.weapon().modeCount());

		LockStatePayload lock = ClientLockState.get();
		variables.put("lock_tracking", ClientLockState.isTracking() ? 1.0 : 0.0);
		variables.put("locked", ClientLockState.isLocked() ? 1.0 : 0.0);
		variables.put("lock_progress", lock.requiredTicks() > 0
				? Math.min(1.0, lock.progressTicks() / (double) lock.requiredTicks())
				: (ClientLockState.isTracking() ? 1.0 : 0.0));
		return variables;
	}

	/** Rounds the player could still load, or -1 when unlimited (no ammo_item, or creative mode). */
	private static int reserveRounds(PlayerEntity player, HandheldDefinition def) {
		if (def.ammoItem().isEmpty() || player.isCreative()) {
			return -1;
		}
		var ammo = Registries.ITEM.get(def.ammoItem().get());
		int count = 0;
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack candidate = inventory.getStack(slot);
			if (candidate.isOf(ammo)) {
				count += candidate.getCount();
			}
		}
		return count * def.roundsPerAmmoItem();
	}

	private static void drawDefault(DrawContext context, MinecraftClient client, ItemStack stack,
			HandheldDefinitions.ClientEntry entry, Map<String, Double> variables, int centerX, int centerY) {
		int x = centerX + 12;
		int y = centerY + 8;
		int lineHeight = client.textRenderer.fontHeight + 2;

		if (entry.weapon().magazineSize() > 0) {
			int reserve = variables.get("reserve_ammo").intValue();
			Text ammo = Text.translatable("hud.tudursguns.ammo",
					variables.get("ammo").intValue(), entry.weapon().magazineSize(),
					reserve < 0 ? "-" : Integer.toString(reserve));
			context.drawTextWithShadow(client.textRenderer, ammo, x, y, 0xFFFFFFFF);
			y += lineHeight;
		}
		if (variables.get("reloading") > 0) {
			context.drawTextWithShadow(client.textRenderer, Text.translatable("hud.tudursguns.reloading",
					(int) Math.round(variables.get("reload_progress") * 100)), x, y, 0xFFFFD040);
			y += lineHeight;
		}
		if (entry.weapon().modeCount() > 1) {
			context.drawTextWithShadow(client.textRenderer, Text.translatable("hud.tudursguns.mode",
					variables.get("mode").intValue(), entry.weapon().modeCount()), x, y, 0xFFC0C0C0);
			y += lineHeight;
		}
		if (variables.get("locked") > 0) {
			context.drawTextWithShadow(client.textRenderer, Text.translatable("hud.tudursguns.locked"), x, y, 0xFFFF4040);
		} else if (variables.get("lock_tracking") > 0) {
			context.drawTextWithShadow(client.textRenderer, Text.translatable("hud.tudursguns.locking",
					(int) Math.round(variables.get("lock_progress") * 100)), x, y, 0xFFFFA040);
		}
	}
}
