package com.example.tudursguns.registry;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.block.AmmoBoxBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModBlockEntities {

	private ModBlockEntities() {
	}

	public static BlockEntityType<AmmoBoxBlockEntity> AMMO_BOX;

	public static void register() {
		AMMO_BOX = Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of(TudursGuns.MOD_ID, "ammo_box"),
				FabricBlockEntityTypeBuilder.create(AmmoBoxBlockEntity::new, ModBlocks.AMMO_BOX).build());
	}
}
