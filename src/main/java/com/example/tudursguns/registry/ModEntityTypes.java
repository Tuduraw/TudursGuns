package com.example.tudursguns.registry;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.entity.LaserSpotEntity;
import com.example.tudursguns.entity.MineEntity;
import com.example.tudursguns.entity.SmokeCloudEntity;
import com.example.tudursguns.entity.SmokeDecoyEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModEntityTypes {

	private ModEntityTypes() {
	}

	public static EntityType<SmokeCloudEntity> SMOKE_CLOUD;
	public static EntityType<SmokeDecoyEntity> SMOKE_DECOY;
	public static EntityType<LaserSpotEntity> LASER_SPOT;
	public static EntityType<MineEntity> MINE;

	public static void register() {
		SMOKE_CLOUD = register("smoke_cloud", EntityType.Builder.<SmokeCloudEntity>create(SmokeCloudEntity::new, SpawnGroup.MISC)
				.dimensions(0.5f, 0.5f).maxTrackingRange(16).disableSaving().disableSummon());
		SMOKE_DECOY = register("smoke_decoy", EntityType.Builder.<SmokeDecoyEntity>create(SmokeDecoyEntity::new, SpawnGroup.MISC)
				.dimensions(0.1f, 0.1f).maxTrackingRange(16).disableSaving().disableSummon());
		FabricDefaultAttributeRegistry.register(SMOKE_DECOY, SmokeDecoyEntity.createAttributes());
		LASER_SPOT = register("laser_spot", EntityType.Builder.<LaserSpotEntity>create(LaserSpotEntity::new, SpawnGroup.MISC)
				.dimensions(0.1f, 0.1f).maxTrackingRange(16).disableSaving().disableSummon());
		FabricDefaultAttributeRegistry.register(LASER_SPOT, SmokeDecoyEntity.createAttributes());
		// Saved with the world (a minefield stays laid); tracked every tick so one stuck to a moving
		// vehicle keeps up with it.
		MINE = register("mine", EntityType.Builder.<MineEntity>create(MineEntity::new, SpawnGroup.MISC)
				.dimensions(0.5f, 0.2f).maxTrackingRange(8).trackingTickInterval(1).disableSummon());
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType.Builder<T> builder) {
		RegistryKey<EntityType<?>> key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(TudursGuns.MOD_ID, path));
		return Registry.register(Registries.ENTITY_TYPE, key, builder.build(key));
	}
}
