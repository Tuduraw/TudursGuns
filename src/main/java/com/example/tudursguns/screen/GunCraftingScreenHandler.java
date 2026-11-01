package com.example.tudursguns.screen;

import com.example.tudursguns.handheld.GunRecipeDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.registry.ModBlocks;
import com.example.tudursguns.registry.ModScreenHandlers;
import com.example.tudursguns.weapon.GunCrafting;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

/** The gun crafting table's screen: a list of recipes, made from the player's own inventory - no
 * slots of its own. The client picks a recipe by its position in the list (the same order on both
 * sides, see GunCrafting.recipeIds) through a button click. */
public class GunCraftingScreenHandler extends AbstractContainerMenu {

	private final ContainerLevelAccess context;

	public GunCraftingScreenHandler(int syncId, Inventory playerInventory) {
		this(syncId, playerInventory, ContainerLevelAccess.NULL);
	}

	public GunCraftingScreenHandler(int syncId, Inventory playerInventory, ContainerLevelAccess context) {
		super(ModScreenHandlers.GUN_CRAFTING, syncId);
		this.context = context;
	}

	/** id: the recipe's position in the list. */
	@Override
	public boolean clickMenuButton(Player player, int id) {
		boolean client = player.level().isClientSide();
		List<Identifier> ids = GunCrafting.recipeIds(client);
		if (id < 0 || id >= ids.size()) {
			return false;
		}
		GunRecipeDefinition recipe = (client ? ModDefinitions.GUN_RECIPES.client() : ModDefinitions.GUN_RECIPES.server()).get(ids.get(id));
		if (recipe == null) {
			return false;
		}
		return client ? GunCrafting.canCraft(player, recipe) : GunCrafting.craft(player, recipe);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.context, player, ModBlocks.GUN_CRAFTING_TABLE);
	}
}
