package com.example.tudursguns.block;

import com.example.tudursguns.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Ammo box: a fixed supply point - see AmmoBoxBlockEntity. (For an OBJ-modelled ammo point, Tudur's
 * Vehicle Mod's static emplacements can already act as supply vehicles.) */
public class AmmoBoxBlock extends BlockWithEntity {

	public static final MapCodec<AmmoBoxBlock> CODEC = createCodec(AmmoBoxBlock::new);

	public AmmoBoxBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new AmmoBoxBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient() ? null : validateTicker(type, ModBlockEntities.AMMO_BOX, AmmoBoxBlockEntity::tick);
	}
}
