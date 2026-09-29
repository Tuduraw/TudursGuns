package com.example.tudursguns.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** A block that only opens a screen when used, and keeps no inventory of its own (like a crafting
 * table): the weapon workbench and the gun crafting table. */
public class ScreenBlock extends Block {

	/** Makes the block's screen handler. */
	public interface HandlerFactory {
		ScreenHandler create(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context);
	}

	private final Text title;
	private final HandlerFactory factory;

	public ScreenBlock(Settings settings, Text title, HandlerFactory factory) {
		super(settings);
		this.title = title;
		this.factory = factory;
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
				this.factory.create(syncId, playerInventory, ScreenHandlerContext.create(world, pos)), this.title);
	}
}
