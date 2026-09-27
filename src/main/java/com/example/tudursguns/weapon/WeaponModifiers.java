package com.example.tudursguns.weapon;

import com.example.tudursguns.handheld.AttachmentDefinition;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.HandheldDefinitions;
import com.example.tudursguns.registry.ModComponents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Optional;

/** The combined effect of the attachments fitted to one weapon stack. Multipliers multiply together
 * and bonuses add up; only attachments the weapon's own definition accepts in that slot count.
 * Works on both sides (definitions are looked up with HandheldDefinitions.getAnyAttachment). */
public record WeaponModifiers(
		float magazineMultiplier,
		int magazineBonus,
		float reloadMultiplier,
		Optional<String> soundOverride,
		float volumeMultiplier,
		float pitchMultiplier,
		float accuracyMultiplier,
		float meleeDamageBonus,
		Identifier zoomAttachment,
		AttachmentDefinition.Zoom zoom,
		Vector3f sightOverride,
		float recoilMultiplier
) {

	public static final WeaponModifiers NONE = new WeaponModifiers(1f, 0, 1f, Optional.empty(), 1f, 1f, 1f, 0f, null, null, null, 1f);

	public static Map<String, Identifier> fitted(ItemStack stack) {
		return stack.getOrDefault(ModComponents.ATTACHMENTS, Map.of());
	}

	public static WeaponModifiers of(ItemStack stack, HandheldDefinition def) {
		Map<String, Identifier> fitted = fitted(stack);
		if (def == null || fitted.isEmpty()) {
			return NONE;
		}
		float magazineMultiplier = 1f;
		int magazineBonus = 0;
		float reloadMultiplier = 1f;
		Optional<String> soundOverride = Optional.empty();
		float volumeMultiplier = 1f;
		float pitchMultiplier = 1f;
		float accuracyMultiplier = 1f;
		float meleeDamageBonus = 0f;
		Identifier zoomAttachment = null;
		AttachmentDefinition.Zoom zoom = null;
		Vector3f sightOverride = null;
		float recoilMultiplier = 1f;
		for (String slot : def.attachmentSlotNames()) {
			Identifier attachmentId = fitted.get(slot);
			HandheldDefinition.AttachmentMount mount = attachmentId == null ? null : def.mountFor(slot, attachmentId);
			AttachmentDefinition attachment = mount == null ? null : HandheldDefinitions.getAnyAttachment(attachmentId);
			if (attachment == null) {
				continue;
			}
			magazineMultiplier *= attachment.magazineSizeMultiplier();
			magazineBonus += attachment.magazineSizeBonus();
			reloadMultiplier *= attachment.reloadTimeMultiplier();
			if (attachment.soundOverride().isPresent()) {
				soundOverride = attachment.soundOverride();
			}
			volumeMultiplier *= attachment.soundVolumeMultiplier();
			pitchMultiplier *= attachment.soundPitchMultiplier();
			accuracyMultiplier *= attachment.accuracyMultiplier();
			meleeDamageBonus += attachment.meleeDamageBonus();
			recoilMultiplier *= attachment.recoilMultiplier();
			if (zoom == null && attachment.zoom().isPresent()) {
				zoom = attachment.zoom().get();
				zoomAttachment = attachmentId;
			}
			if (sightOverride == null && mount.sightPosition().isPresent()) {
				sightOverride = mount.sightPosition().get();
			}
		}
		return new WeaponModifiers(magazineMultiplier, magazineBonus, reloadMultiplier, soundOverride, volumeMultiplier,
				pitchMultiplier, accuracyMultiplier, meleeDamageBonus, zoomAttachment, zoom, sightOverride, recoilMultiplier);
	}

	/** The weapon file's Round adjusted by attachments. 0 (no magazine) stays 0. */
	public int magazineSize(int base) {
		if (base <= 0) {
			return base;
		}
		return Math.max(1, Math.round(base * this.magazineMultiplier) + this.magazineBonus);
	}

	/** The weapon file's ReloadTime adjusted by attachments (at least 1 tick). */
	public int reloadTicks(int base) {
		return Math.max(1, Math.round(Math.max(1, base) * this.reloadMultiplier));
	}

	public boolean hasZoom() {
		return this.zoom != null;
	}
}
