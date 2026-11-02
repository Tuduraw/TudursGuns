package com.example.tudursguns.weapon;

import com.example.tudursguns.handheld.AmmoDefinition;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.registry.ModItems;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** What a weapon reloads from: a magazine item of an ammo definition ("ammo"), or a plain item
 * ("ammo_item"), and how many rounds each gives. Works on both sides. */
public record AmmoSupply(Predicate<ItemStack> matches, int roundsPerItem) {

	/** null when the weapon needs no ammunition at all. */
	public static AmmoSupply of(HandheldDefinition def) {
		if (def.handling().ammo().isPresent()) {
			Identifier ammoId = def.handling().ammo().get();
			AmmoDefinition ammo = ModDefinitions.AMMO.getAny(ammoId);
			return new AmmoSupply(stack -> stack.is(ModItems.AMMO) && ammoId.equals(stack.get(ModComponents.AMMO_TYPE)),
					ammo == null ? 1 : ammo.rounds());
		}
		if (def.ammoItem().isPresent()) {
			Item item = BuiltInRegistries.ITEM.getValue(def.ammoItem().get());
			return new AmmoSupply(stack -> stack.is(item), def.roundsPerAmmoItem());
		}
		return null;
	}

	public int countItems(Player player) {
		return countMatching(player, this.matches);
	}

	/** How many items in the player's inventory match. */
	public static int countMatching(Player player, Predicate<ItemStack> matches) {
		int count = 0;
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack candidate = inventory.getItem(slot);
			if (matches.test(candidate)) {
				count += candidate.getCount();
			}
		}
		return count;
	}
}
