package com.example.tudursguns.registry;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.TudursGunsConfig;
import com.example.tudursguns.entity.LaserSpotEntity;
import com.example.tudursguns.entity.MineEntity;
import com.example.tudursguns.entity.SmokeCloudEntity;
import com.example.tudursguns.entity.SmokeDecoyEntity;
import com.example.tudursguns.soldier.EnemySoldierEntity;
import com.example.tudursguns.soldier.FriendlySoldierEntity;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;

public final class ModEntityTypes {

	private ModEntityTypes() {
	}

	public static EntityType<SmokeCloudEntity> SMOKE_CLOUD;
	public static EntityType<SmokeDecoyEntity> SMOKE_DECOY;
	public static EntityType<LaserSpotEntity> LASER_SPOT;
	public static EntityType<MineEntity> MINE;
	public static EntityType<FriendlySoldierEntity> SOLDIER;
	public static EntityType<EnemySoldierEntity> ENEMY_SOLDIER;
	public static Item ENEMY_SOLDIER_SPAWN_EGG;

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

		// Only a soldier post sends these out (a summoned one has no post and would vanish at once).
		SOLDIER = register("soldier", EntityType.Builder.<FriendlySoldierEntity>create(FriendlySoldierEntity::new, SpawnGroup.MISC)
				.dimensions(0.6f, 1.8f).eyeHeight(1.62f).maxTrackingRange(10).disableSummon());
		FabricDefaultAttributeRegistry.register(SOLDIER, FriendlySoldierEntity.createAttributes());
		ENEMY_SOLDIER = register("enemy_soldier", EntityType.Builder.<EnemySoldierEntity>create(EnemySoldierEntity::new, SpawnGroup.MONSTER)
				.dimensions(0.6f, 1.8f).eyeHeight(1.62f).maxTrackingRange(10));
		FabricDefaultAttributeRegistry.register(ENEMY_SOLDIER, EnemySoldierEntity.createAttributes());
		SpawnRestriction.register(ENEMY_SOLDIER, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnInDark);
		TudursGunsConfig.Data config = TudursGunsConfig.get();
		if (config.enemy_soldier_natural_spawn && config.enemy_soldier_spawn_weight > 0) {
			BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, ENEMY_SOLDIER,
					config.enemy_soldier_spawn_weight, config.enemy_soldier_group_min, config.enemy_soldier_group_max);
		}
		RegistryKey<Item> eggKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(TudursGuns.MOD_ID, "enemy_soldier_spawn_egg"));
		ENEMY_SOLDIER_SPAWN_EGG = Registry.register(Registries.ITEM, eggKey, new SpawnEggItem(new Item.Settings().registryKey(eggKey).spawnEgg(ENEMY_SOLDIER)));
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType.Builder<T> builder) {
		RegistryKey<EntityType<?>> key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(TudursGuns.MOD_ID, path));
		return Registry.register(Registries.ENTITY_TYPE, key, builder.build(key));
	}
}
