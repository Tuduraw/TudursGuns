package com.example.tudursguns.screen;

import com.example.tudursguns.handheld.GunRecipeDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.registry.ModBlocks;
import com.example.tudursguns.registry.ModScreenHandlers;
import com.example.tudursguns.weapon.GunCrafting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.util.Identifier;

import java.util.List;

/** The gun crafting table's screen: a list of recipes, made from the player's own inventory - no
 * slots of its own. The client picks a recipe by its position in the list (the same order on both
 * sides, see GunCrafting.recipeIds) through a button click. */
public class GunCraftingScreenHandler extends ScreenHandler {

	private final ScreenHandlerContext context;

	public GunCraftingScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, ScreenHandlerContext.EMPTY);
	}

	public GunCraftingScreenHandler(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
		super(ModScreenHandlers.GUN_CRAFTING, syncId);
		this.context = context;
	}

	/** id: the recipe's position in the list. */
	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		boolean client = player.getEntityWorld().isClient();
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
	public ItemStack quickMove(PlayerEntity player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return canUse(this.context, player, ModBlocks.GUN_CRAFTING_TABLE);
	}
}
