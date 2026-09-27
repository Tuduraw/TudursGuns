package com.example.tudursguns.block;

import com.example.tudursguns.screen.GunCraftingScreenHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Gun crafting table: opens a screen listing the gun recipes (GunRecipeDefinition) and makes them
 * from the player's inventory. Keeps no inventory of its own. */
public class GunCraftingTableBlock extends Block {

	private static final Text TITLE = Text.translatable("container.tudursguns.gun_crafting_table");

	public GunCraftingTableBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
			serverPlayer.openHandledScreen(state.createScreenHandlerFactory(world, pos));
		}
		return ActionResult.SUCCESS;
	}

	@Override
	protected NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, World world, BlockPos pos) {
		return new SimpleNamedScreenHandlerFactory((syncId, playerInventory, player) ->
				new GunCraftingScreenHandler(syncId, playerInventory, ScreenHandlerContext.create(world, pos)), TITLE);
	}
}
