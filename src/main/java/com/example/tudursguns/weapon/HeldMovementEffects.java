package com.example.tudursguns.weapon;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.handheld.AttachmentDefinition;
import com.example.tudursguns.handheld.EquipmentDefinition;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.HeldMovement;
import com.example.tudursguns.handheld.MineDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.handheld.ThrowableDefinition;
import com.example.tudursguns.item.EquipmentItem;
import com.example.tudursguns.item.HandheldWeaponItem;
import com.example.tudursguns.item.MineItem;
import com.example.tudursguns.item.ThrowableItem;
import com.example.tudursguns.registry.ModComponents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.Map;

/** Applies HeldMovement: every tick, each player's walking speed gets one temporary modifier (same
 * kind as armor's movement_speed - a fraction of the total speed) worth everything they hold: both
 * hands, a weapon's fitted attachments, and the aiming values while aiming. No modifier at all when
 * the total is 0. Temporary, so it's never saved and simply disappears with the item. */
public final class HeldMovementEffects {

	private HeldMovementEffects() {
	}

	private static final Identifier MODIFIER_ID = Identifier.of(TudursGuns.MOD_ID, "held_item_movement");

	public static void tickWorld(ServerWorld world) {
		for (ServerPlayerEntity player : world.getPlayers(candidate -> true)) {
			apply(player, player.isSpectator() ? 0f : total(player));
		}
	}

	private static float total(ServerPlayerEntity player) {
		boolean aiming = HandheldCombat.isAimKeyHeld(player)
				|| (player.isUsingItem() && player.getActiveHand() == Hand.MAIN_HAND
						&& player.getActiveItem().getItem() instanceof HandheldWeaponItem);
		float total = 0f;
		for (Hand hand : Hand.values()) {
			total += contribution(player.getStackInHand(hand), aiming && hand == Hand.MAIN_HAND);
		}
		return Math.max(-1f, total);
	}

	/** What one held stack adds (0 for anything that isn't this mod's). */
	public static float contribution(ItemStack stack, boolean aiming) {
		Item item = stack.getItem();
		if (item instanceof HandheldWeaponItem) {
			HandheldDefinition def = ModDefinitions.HANDHELD.getServer(stack.get(ModComponents.WEAPON));
			if (def == null) {
				return 0f;
			}
			float value = def.movement().total(aiming);
			for (Map.Entry<String, Identifier> fitted : WeaponModifiers.fitted(stack).entrySet()) {
				AttachmentDefinition attachment = def.mountFor(fitted.getKey(), fitted.getValue()) == null ? null
						: ModDefinitions.ATTACHMENTS.getAny(fitted.getValue());
				if (attachment != null) {
					value += attachment.movement().total(aiming);
				}
			}
			return value;
		}
		HeldMovement movement = null;
		if (item instanceof ThrowableItem) {
			ThrowableDefinition def = ModDefinitions.THROWABLES.getServer(stack.get(ModComponents.THROWABLE));
			movement = def == null ? null : def.movement();
		} else if (item instanceof MineItem) {
			MineDefinition def = ModDefinitions.MINES.getServer(stack.get(ModComponents.MINE));
			movement = def == null ? null : def.movement();
		} else if (item instanceof EquipmentItem) {
			EquipmentDefinition def = ModDefinitions.EQUIPMENT.getServer(stack.get(ModComponents.EQUIPMENT));
			movement = def == null ? null : def.movement();
		}
		return movement == null ? 0f : movement.speed();
	}

	private static void apply(ServerPlayerEntity player, float value) {
		EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		EntityAttributeModifier current = speed.getModifier(MODIFIER_ID);
		if (value == 0f) {
			if (current != null) {
				speed.removeModifier(MODIFIER_ID);
			}
			return;
		}
		if (current == null || current.value() != value) {
			if (current != null) {
				speed.removeModifier(MODIFIER_ID);
			}
			speed.addTemporaryModifier(new EntityAttributeModifier(MODIFIER_ID, value, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}
}
