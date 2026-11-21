package com.example.tudursguns.weapon;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.handheld.AmmoDefinition;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.registry.ModItems;
import com.example.tudursvehiclemod.asset.WeaponStatsLoader;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** What a weapon reloads from, and how many rounds each item gives. Works on both sides.
 *
 * - "ammo": an ammo definition's items (a magazine, a clip...); "ammo": "none" - nothing, the weapon
 *   can't be reloaded.
 * - "ammo_item": a plain item, rounds_per_ammo_item rounds each.
 * - Neither: the universal ammo (tudursguns:generic) - so an addon weapon without ammo settings still
 *   needs ammunition. Its items fill a whole magazine each (full_magazine).
 *
 * ammo: the ammo definition (null for a plain item or none); reloadable: false for "none". */
public record AmmoSupply(Predicate<ItemStack> matches, int roundsPerItem, Identifier ammo, Item item, boolean reloadable) {

	/** "ammo": "none" (it reads as minecraft:none). */
	public static final Identifier NONE = Identifier.withDefaultNamespace("none");
	/** The universal ammo: what a weapon with no ammo settings reloads from. */
	public static final Identifier GENERIC = Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, "generic");

	private static final AmmoSupply NOT_RELOADABLE = new AmmoSupply(stack -> false, 0, null, null, false);

	/** What def reloads from; magazineSize (after attachments) is what a full_magazine item gives. */
	public static AmmoSupply of(HandheldDefinition def, int magazineSize) {
		return of(def.handling().ammo(), def.ammoItem(), def.roundsPerAmmoItem(), magazineSize);
	}

	/** The same for a stack: its magazine size after attachments (of whatever def fires - see Firing). */
	public static AmmoSupply of(ItemStack stack, HandheldDefinition def) {
		return of(def, WeaponModifiers.of(stack, def).magazineSize(WeaponStatsLoader.get(def.weapon()).magazineSize()));
	}

	public static AmmoSupply of(Optional<Identifier> ammo, Optional<Identifier> ammoItem, int roundsPerAmmoItem, int magazineSize) {
		if (ammo.isPresent() && NONE.equals(ammo.get())) {
			return NOT_RELOADABLE;
		}
		if (ammo.isEmpty() && ammoItem.isPresent()) {
			Item item = BuiltInRegistries.ITEM.getValue(ammoItem.get());
			return new AmmoSupply(stack -> stack.is(item), roundsPerAmmoItem, null, item, true);
		}
		Identifier ammoId = ammo.orElse(GENERIC);
		AmmoDefinition def = ModDefinitions.AMMO.getAny(ammoId);
		// Without its definition (a data pack removed it), the universal ammo still fills a magazine.
		boolean fullMagazine = def != null ? def.fullMagazine() : GENERIC.equals(ammoId);
		int rounds = fullMagazine ? Math.max(1, magazineSize) : def != null ? def.rounds() : 1;
		return new AmmoSupply(stack -> stack.is(ModItems.AMMO) && ammoId.equals(stack.get(ModComponents.AMMO_TYPE)),
				rounds, ammoId, null, true);
	}

	public int countItems(Player player) {
		return countMatching(player, this.matches);
	}

	/** For tooltips: the ammo's name, or that the weapon can't be reloaded. */
	public MutableComponent describe() {
		if (!this.reloadable) {
			return Component.translatable("tooltip.tudursguns.not_reloadable");
		}
		if (this.item != null) {
			return Component.translatable("tooltip.tudursguns.ammo_type", new ItemStack(this.item).getHoverName());
		}
		String name = ModDefinitions.AMMO.name(this.ammo);
		return Component.translatable("tooltip.tudursguns.ammo_type", name != null ? name : this.ammo.toString());
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
