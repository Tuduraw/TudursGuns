package com.example.tudursguns.soldier;

import com.example.tudursguns.TudursGunsConfig;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;

/** A hostile soldier that roams like any monster and attacks players and posted soldiers with the
 * gear it spawned with (EnemySoldierGear). Its rounds are endless - it reloads when its magazine is
 * empty. Spawns naturally unless the server config turns that off. */
public class EnemySoldierEntity extends HostileEntity implements Soldier {

	private static final TrackedData<Boolean> AIMING = DataTracker.registerData(EnemySoldierEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	public EnemySoldierEntity(EntityType<? extends EnemySoldierEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.MAX_HEALTH, 20.0)
				.add(EntityAttributes.MOVEMENT_SPEED, 0.25)
				.add(EntityAttributes.FOLLOW_RANGE, 48.0)
				.add(EntityAttributes.ATTACK_DAMAGE, 3.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(AIMING, false);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(2, new SoldierWeaponGoal<>(this, 1.0));
		// Unarmed (no weapon had a soldier_weight): fists.
		this.goalSelector.add(3, new MeleeAttackGoal(this, 1.0, false));
		this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.8));
		this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8f));
		this.goalSelector.add(7, new LookAroundGoal(this));
		this.targetSelector.add(1, new RevengeGoal(this));
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
		this.targetSelector.add(3, new ActiveTargetGoal<>(this, FriendlySoldierEntity.class, true));
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, EntityData entityData) {
		EntityData data = super.initialize(world, difficulty, spawnReason, entityData);
		EnemySoldierGear.equip(this, world.getRandom());
		return data;
	}

	@Override
	public boolean isAimingWeapon() {
		return this.dataTracker.get(AIMING);
	}

	@Override
	public void setAimingWeapon(boolean aiming) {
		this.dataTracker.set(AIMING, aiming);
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
