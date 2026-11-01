package com.example.tudursguns.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A block that only opens a screen when used, and keeps no inventory of its own (like a crafting
 * table): the weapon workbench and the gun crafting table. */
public class ScreenBlock extends Block {

	/** Makes the block's screen handler. */
	public interface HandlerFactory {
		AbstractContainerMenu create(int syncId, Inventory playerInventory, ContainerLevelAccess context);
	}

	private final Component title;
	private final HandlerFactory factory;

	public ScreenBlock(Properties settings, Component title, HandlerFactory factory) {
		super(settings);
		this.title = title;
		this.factory = factory;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
		if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer) {
			serverPlayer.openMenu(state.getMenuProvider(world, pos));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected MenuProvider getMenuProvider(BlockState state, Level world, BlockPos pos) {
		return new SimpleMenuProvider((syncId, playerInventory, player) ->
				this.factory.create(syncId, playerInventory, ContainerLevelAccess.create(world, pos)), this.title);
	}
}
