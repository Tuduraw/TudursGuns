package com.example.tudursguns.weapon;

import com.example.tudursguns.handheld.AmmoDefinition;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.registry.ModItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.function.Predicate;

/** What a weapon reloads from: a magazine item of an ammo definition ("ammo"), or a plain item
 * ("ammo_item"), and how many rounds each gives. Works on both sides. */
public record AmmoSupply(Predicate<ItemStack> matches, int roundsPerItem) {

	/** null when the weapon needs no ammunition at all. */
	public static AmmoSupply of(HandheldDefinition def) {
		if (def.handling().ammo().isPresent()) {
			Identifier ammoId = def.handling().ammo().get();
			AmmoDefinition ammo = ModDefinitions.AMMO.getAny(ammoId);
			return new AmmoSupply(stack -> stack.isOf(ModItems.AMMO) && ammoId.equals(stack.get(ModComponents.AMMO_TYPE)),
					ammo == null ? 1 : ammo.rounds());
		}
		if (def.ammoItem().isPresent()) {
			Item item = Registries.ITEM.get(def.ammoItem().get());
			return new AmmoSupply(stack -> stack.isOf(item), def.roundsPerAmmoItem());
		}
		return null;
	}

	public int countItems(PlayerEntity player) {
		return countMatching(player, this.matches);
	}

	/** How many items in the player's inventory match. */
	public static int countMatching(PlayerEntity player, Predicate<ItemStack> matches) {
		int count = 0;
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack candidate = inventory.getStack(slot);
			if (matches.test(candidate)) {
				count += candidate.getCount();
			}
		}
		return count;
	}
}
