package com.example.tudursguns.weapon;

import com.example.tudursguns.handheld.GunRecipeDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** The gun crafting table's recipes (GunRecipeDefinition): the order they're listed in, whether a
 * player has what one needs, and making it. Works on both sides: the list order is the same on each
 * (sorted by category, then id), which is what lets the client name a recipe by its position. */
public final class GunCrafting {

	private GunCrafting() {
	}

	/** Recipe ids in list order - the server's recipes on the server, the synced copy on a client. */
	public static List<Identifier> recipeIds(boolean client) {
		Map<Identifier, GunRecipeDefinition> recipes = client ? ModDefinitions.GUN_RECIPES.client() : ModDefinitions.GUN_RECIPES.server();
		List<Identifier> ids = new ArrayList<>(recipes.keySet());
		ids.sort(Comparator.comparing((Identifier id) -> recipes.get(id).category().orElse(""))
				.thenComparing(Identifier::toString));
		return ids;
	}

	/** How many of an ingredient the player has (never more than needed matters). */
	public static int count(PlayerEntity player, GunRecipeDefinition.ItemRef ref) {
		int total = 0;
		PlayerInventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (ref.matches(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/** Whether every ingredient can be taken (the same stack isn't counted twice for two ingredients). */
	public static boolean canCraft(PlayerEntity player, GunRecipeDefinition recipe) {
		return player.isCreative() || take(player, recipe, true);
	}

	/** Takes the ingredients and gives the result. Returns false (and changes nothing) if something's missing. */
	public static boolean craft(PlayerEntity player, GunRecipeDefinition recipe) {
		ItemStack result = recipe.result().createStack();
		if (result.isEmpty()) {
			return false;
		}
		if (!player.isCreative()) {
			if (!take(player, recipe, true)) {
				return false;
			}
			take(player, recipe, false);
		}
		player.getInventory().offerOrDrop(result);
		return true;
	}

	/** simulate: only check, against counts of a copy of the inventory. */
	private static boolean take(PlayerEntity player, GunRecipeDefinition recipe, boolean simulate) {
		PlayerInventory inventory = player.getInventory();
		int[] left = new int[inventory.size()];
		for (int slot = 0; slot < left.length; slot++) {
			left[slot] = inventory.getStack(slot).getCount();
		}
		for (GunRecipeDefinition.ItemRef ingredient : recipe.ingredients()) {
			int needed = ingredient.count();
			for (int slot = 0; slot < left.length && needed > 0; slot++) {
				ItemStack stack = inventory.getStack(slot);
				if (left[slot] <= 0 || !ingredient.matches(stack)) {
					continue;
				}
				int taken = Math.min(needed, left[slot]);
				left[slot] -= taken;
				needed -= taken;
				if (!simulate) {
					stack.decrement(taken);
				}
			}
			if (needed > 0) {
				return false;
			}
		}
		return true;
	}
}
