package com.example.tudursguns.weapon;

import com.example.tudursguns.handheld.AttachmentDefinition;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.HandheldDefinitions;
import com.example.tudursguns.handheld.HandheldEffects;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursvehiclemod.asset.WeaponStats;
import com.example.tudursvehiclemod.asset.WeaponStatsLoader;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.Map;

/** What a weapon stack fires right now: its own weapon, or - with an underbarrel launcher fitted and
 * selected - the launcher's. The launcher is presented as a HandheldDefinition of its own (weapon
 * file, ammo, projectile, semi-auto, no attachments), so firing and reloading work unchanged; its
 * loaded rounds live in their own component (ALT_AMMO). */
public record Firing(HandheldDefinition definition, WeaponStats stats, boolean underbarrel) {

	/** An underbarrel launcher flashes, but throws nothing out (its case comes out by hand on reload). */
	private static final HandheldEffects LAUNCHER_EFFECTS = new HandheldEffects(java.util.Optional.empty(),
			java.util.Optional.of(HandheldEffects.Ejection.OFF), java.util.Optional.of(HandheldEffects.Ejection.OFF));

	public static Firing of(ItemStack stack, HandheldDefinition base) {
		if (stack.getOrDefault(ModComponents.ALT_SELECTED, false)) {
			AttachmentDefinition.Underbarrel launcher = underbarrel(stack, base);
			if (launcher != null) {
				HandheldDefinition def = new HandheldDefinition(launcher.weapon(), base.displayName(), base.model(), base.texture(),
						launcher.projectileItem(), launcher.ammoItem(), launcher.roundsPerAmmoItem(), HandheldDefinition.FireMode.SEMI,
						launcher.muzzleOffset().orElse(base.muzzleOffset()), base.inheritShooterVelocity(),
						new HandheldDefinition.Presentation(base.hud(), launcher.reloadSound(), LAUNCHER_EFFECTS),
						base.display(), base.aim(), Map.of(), base.handling().withoutAmmo(), base.animation());
				return new Firing(def, WeaponStatsLoader.get(launcher.weapon()), true);
			}
		}
		return new Firing(base, WeaponStatsLoader.get(base.weapon()), false);
	}

	/** The fitted underbarrel launcher (one the weapon accepts in that slot), or null. */
	public static AttachmentDefinition.Underbarrel underbarrel(ItemStack stack, HandheldDefinition base) {
		if (base == null) {
			return null;
		}
		for (Map.Entry<String, Identifier> entry : WeaponModifiers.fitted(stack).entrySet()) {
			if (base.mountFor(entry.getKey(), entry.getValue()) == null) {
				continue;
			}
			AttachmentDefinition attachment = HandheldDefinitions.getAnyAttachment(entry.getValue());
			if (attachment != null && attachment.underbarrel().isPresent()) {
				return attachment.underbarrel().get();
			}
		}
		return null;
	}

	/** Where the rounds of whatever is selected are counted. */
	public static ComponentType<Integer> ammoComponent(ItemStack stack) {
		return stack.getOrDefault(ModComponents.ALT_SELECTED, false) ? ModComponents.ALT_AMMO : ModComponents.AMMO;
	}
}
