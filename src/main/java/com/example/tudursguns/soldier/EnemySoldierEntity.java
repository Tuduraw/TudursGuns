package com.example.tudursguns.soldier;

import com.example.tudursguns.TudursGunsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;

/** A hostile soldier that roams like any monster and attacks players and posted soldiers with the
 * gear it spawned with (EnemySoldierGear). Its rounds are endless - it reloads when its magazine is
 * empty. Spawns naturally unless the server config turns that off. */
public class EnemySoldierEntity extends Monster implements Soldier {

	private static final EntityDataAccessor<Boolean> AIMING = SynchedEntityData.defineId(EnemySoldierEntity.class, EntityDataSerializers.BOOLEAN);

	public EnemySoldierEntity(EntityType<? extends EnemySoldierEntity> type, Level world) {
		super(type, world);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.ATTACK_DAMAGE, 3.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(AIMING, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new SoldierWeaponGoal<>(this, 1.0));
		// Unarmed (no weapon had a soldier_weight): fists.
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, false));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, FriendlySoldierEntity.class, true));
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, EntitySpawnReason spawnReason, SpawnGroupData entityData) {
		SpawnGroupData data = super.finalizeSpawn(world, difficulty, spawnReason, entityData);
		EnemySoldierGear.equip(this, world.getRandom());
		return data;
	}

	/** Staying put unless the server config lets them despawn. (Mobs that can't despawn don't count
	 * against the monster cap, so natural spawns are limited by canSpawn's density check instead.) */
	@Override
	public boolean requiresCustomPersistence() {
		return !TudursGunsConfig.get().enemy_soldier_despawn || super.requiresCustomPersistence();
	}

	/** Spawn rule: where monsters spawn (in the dark), and - for natural spawns - not where
	 * enemy_soldier_density_max enemy soldiers are already within enemy_soldier_density_radius. */
	public static boolean canSpawn(EntityType<EnemySoldierEntity> type, ServerLevelAccessor world, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		if (!Monster.checkMonsterSpawnRules(type, world, reason, pos, random)) {
			return false;
		}
		if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
			return true;
		}
		TudursGunsConfig.Data config = TudursGunsConfig.get();
		int nearby = world.getEntitiesOfClass(EnemySoldierEntity.class, new AABB(pos).inflate(config.enemy_soldier_density_radius),
				EnemySoldierEntity::isAlive).size();
		return nearby < config.enemy_soldier_density_max;
	}

	@Override
	public boolean isAimingWeapon() {
		return this.entityData.get(AIMING);
	}

	@Override
	public void setAimingWeapon(boolean aiming) {
		this.entityData.set(AIMING, aiming);
	}

	@Override
	public boolean hasUnlimitedAmmo() {
		return true;
	}

	@Override
	public boolean mayFire() {
		return true;
	}

	@Override
	public double engageRange() {
		return TudursGunsConfig.get().enemy_soldier_engage_range;
	}
}
