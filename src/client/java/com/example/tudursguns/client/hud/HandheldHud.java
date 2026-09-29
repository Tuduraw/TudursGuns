package com.example.tudursguns.client.hud;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.client.AimController;
import com.example.tudursguns.client.ClientLockState;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.handheld.WeaponSummary;
import com.example.tudursguns.item.HandheldWeaponItem;
import com.example.tudursguns.network.LockStatePayload;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.weapon.Firing;
import com.example.tudursguns.weapon.HandheldCombat;
import com.example.tudursguns.weapon.WeaponModifiers;
import com.example.tudursvehiclemod.client.hud.HudExecutionContext;
import com.example.tudursvehiclemod.client.hud.HudScript;
import com.example.tudursvehiclemod.client.hud.HudScriptLoader;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
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
		Identifier id = stack.get(ModComponents.WEAPON);
		HandheldDefinition def = ModDefinitions.HANDHELD.getAny(id);
		WeaponSummary weapon = ModDefinitions.HANDHELD.weapon(id);
		if (def == null || weapon == null) {
			return;
		}

		Map<String, Double> variables = buildVariables(client, player, stack, def, weapon);
		int centerX = context.getScaledWindowWidth() / 2;
		int centerY = context.getScaledWindowHeight() / 2;

		String scriptName = def.hud().orElse(null);
		Map<String, HudScript> scripts = HudScriptLoader.getScripts();
		if (scriptName != null && scripts.containsKey(scriptName)) {
			Map<String, String> stringVariables = new HashMap<>();
			stringVariables.put("weapon_name", stack.getName().getString());
			new HudExecutionContext(context, client, variables, stringVariables, centerX, centerY, scripts).run(scriptName);
			return;
		}
		drawDefault(context, client, weapon, variables, centerX, centerY);
	}

	/** The weapon in either hand (main hand first), or null. */
	public static ItemStack heldWeapon(PlayerEntity player) {
		for (Hand hand : Hand.values()) {
			ItemStack stack = player.getStackInHand(hand);
			if (HandheldWeaponItem.isWeapon(stack)) {
				return stack;
			}
		}
		return null;
	}

	/** Variables available to a handheld HUD script:
	 * ammo, max_ammo (after attachments), reserve_ammo (-1 = unlimited), reloading (0/1),
	 * reload_progress (0-1), aiming (0/1), scoped (0/1), zoom (magnification, 1 when not scoped),
	 * mode (1-based), mode_count, lock_tracking (0/1), locked (0/1), lock_progress (0-1),
	 * underbarrel_fitted (0/1), underbarrel (0/1: the launcher is selected), underbarrel_ammo.
	 * ammo/max_ammo/reload are the weapon's own magazine even while the launcher is selected. */
	private static Map<String, Double> buildVariables(MinecraftClient client, PlayerEntity player, ItemStack stack,
			HandheldDefinition def, WeaponSummary weapon) {
		Map<String, Double> variables = new HashMap<>();
		WeaponModifiers modifiers = WeaponModifiers.of(stack, def);
		int magazineSize = modifiers.magazineSize(weapon.magazineSize());
		int reloadTicks = modifiers.reloadTicks(weapon.reloadTicks());
		variables.put("ammo", (double) stack.getOrDefault(ModComponents.AMMO, 0));
		variables.put("max_ammo", (double) magazineSize);
		int reserve = HandheldCombat.availableRounds(player, def);
		variables.put("reserve_ammo", reserve == Integer.MAX_VALUE ? -1.0 : reserve);

		Long reloadUntil = stack.get(ModComponents.RELOAD_UNTIL);
		boolean reloading = reloadUntil != null;
		double reloadProgress = 0.0;
		if (reloading) {
			long remaining = reloadUntil - client.world.getTime();
			reloadProgress = Math.max(0.0, Math.min(1.0, 1.0 - remaining / (double) reloadTicks));
		}
		variables.put("reloading", reloading ? 1.0 : 0.0);
		variables.put("reload_progress", reloadProgress);

		variables.put("aiming", AimController.isAiming() ? 1.0 : 0.0);
		variables.put("scoped", AimController.isScoped() ? 1.0 : 0.0);
		variables.put("zoom", AimController.isScoped() ? (double) AimController.magnification() : 1.0);
		variables.put("mode", (double) (stack.getOrDefault(ModComponents.MODE, 0) + 1));
		variables.put("mode_count", (double) weapon.modeCount());
		boolean hasLauncher = Firing.underbarrel(stack, def) != null;
		variables.put("underbarrel_fitted", hasLauncher ? 1.0 : 0.0);
		variables.put("underbarrel", hasLauncher && stack.getOrDefault(ModComponents.ALT_SELECTED, false) ? 1.0 : 0.0);
		variables.put("underbarrel_ammo", (double) stack.getOrDefault(ModComponents.ALT_AMMO, 0));

		LockStatePayload lock = ClientLockState.get();
		variables.put("lock_tracking", ClientLockState.isTracking() ? 1.0 : 0.0);
		variables.put("locked", ClientLockState.isLocked() ? 1.0 : 0.0);
		variables.put("lock_progress", lock.requiredTicks() > 0
				? Math.min(1.0, lock.progressTicks() / (double) lock.requiredTicks())
				: (ClientLockState.isTracking() ? 1.0 : 0.0));
		return variables;
	}

	private static void drawDefault(DrawContext context, MinecraftClient client, WeaponSummary weapon,
			Map<String, Double> variables, int centerX, int centerY) {
		int x = centerX + 12;
		int y = centerY + 8;
		int lineHeight = client.textRenderer.fontHeight + 2;

		if (variables.get("max_ammo") > 0) {
			int reserve = variables.get("reserve_ammo").intValue();
			Text ammo = Text.translatable("hud.tudursguns.ammo",
					variables.get("ammo").intValue(), variables.get("max_ammo").intValue(),
					reserve < 0 ? "-" : Integer.toString(reserve));
			context.drawTextWithShadow(client.textRenderer, ammo, x, y, 0xFFFFFFFF);
			y += lineHeight;
		}
		if (variables.get("underbarrel_fitted") > 0) {
			boolean selected = variables.get("underbarrel") > 0;
			context.drawTextWithShadow(client.textRenderer, Text.translatable(selected ? "hud.tudursguns.underbarrel.selected" : "hud.tudursguns.underbarrel",
					variables.get("underbarrel_ammo").intValue()), x, y, selected ? 0xFFFFD040 : 0xFF909090);
			y += lineHeight;
		}
		if (variables.get("reloading") > 0) {
			context.drawTextWithShadow(client.textRenderer, Text.translatable("hud.tudursguns.reloading",
					(int) Math.round(variables.get("reload_progress") * 100)), x, y, 0xFFFFD040);
			y += lineHeight;
		}
		if (weapon.modeCount() > 1) {
			context.drawTextWithShadow(client.textRenderer, Text.translatable("hud.tudursguns.mode",
					variables.get("mode").intValue(), weapon.modeCount()), x, y, 0xFFC0C0C0);
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
