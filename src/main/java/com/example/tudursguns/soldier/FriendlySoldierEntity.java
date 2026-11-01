package com.example.tudursguns.soldier;

import com.example.tudursguns.block.SoldierPostBlockEntity;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.item.HandheldWeaponItem;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.weapon.WeaponModifiers;
import com.example.tudursvehiclemod.asset.WeaponStatsLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The soldier a soldier post sends out (SoldierPostBlockEntity). It wears and carries what is in the
 * post's slots - the very same stacks, so rounds fired and armor worn down show in the post, and
 * taking a piece out of the post takes it off the soldier. It walks the post's route
 * (SoldierPatrolGoal), fights monsters near the route (SoldierTargetGoal), goes back to the post
 * to wait for an ammo box when its magazine is empty, and walks back when the post runs out of food
 * or is stood down (then it goes into the post). It disappears if its post is gone. */
public class FriendlySoldierEntity extends PathfinderMob implements Soldier {

	private static final EntityDataAccessor<Boolean> AIMING = SynchedEntityData.defineId(FriendlySoldierEntity.class, EntityDataSerializers.BOOLEAN);
	private static final int HEAL_INTERVAL_TICKS = 40;

	private BlockPos postPos = BlockPos.ZERO;
	/** The post, looked up once a tick (null while it isn't loaded). */
	private SoldierPostBlockEntity post;

	public FriendlySoldierEntity(EntityType<? extends FriendlySoldierEntity> type, Level world) {
		super(type, world);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			this.setDropChance(slot, 0f);
		}
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FOLLOW_RANGE, 64.0);
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
		this.goalSelector.addGoal(4, new SoldierPatrolGoal(this, 0.8));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new SoldierTargetGoal(this));
	}

	public void setPost(BlockPos pos) {
		this.postPos = pos.immutable();
	}

	public BlockPos postPos() {
		return this.postPos;
	}

	/** The post, or null while it isn't loaded. */
	public SoldierPostBlockEntity post() {
		return this.post;
	}

	@Override
	public void tick() {
		if (this.level() instanceof ServerLevel world && !this.isRemoved()) {
			this.post = null;
			if (world.isLoaded(this.postPos)) {
				if (!(world.getBlockEntity(this.postPos) instanceof SoldierPostBlockEntity found) || !found.isSoldier(this)) {
					this.discard();
					return;
				}
				this.post = found;
				found.equip(this);
			}
		}
		super.tick();
	}

	@Override
	protected void customServerAiStep(ServerLevel world) {
		super.customServerAiStep(world);
		if (this.tickCount % HEAL_INTERVAL_TICKS == 0 && this.getTarget() == null && this.post != null && this.post.hasFood()
				&& this.getHealth() < this.getMaxHealth()) {
			this.heal(1f);
		}
	}

	// ---------------------------------------------------------------- what it should do

	/** Whether the post wants it back: stood down, or out of food. */
	public boolean mustReturn() {
		return this.post != null && (!this.post.isActive() || !this.post.hasFood());
	}

	/** Its magazine is empty (and the weapon uses one). */
	public boolean outOfAmmo() {
		ItemStack stack = this.getMainHandItem();
		HandheldDefinition def = HandheldWeaponItem.serverDefinition(stack);
		if (def == null) {
			return false;
		}
		int magazineSize = WeaponModifiers.of(stack, def).magazineSize(WeaponStatsLoader.get(def.weapon()).magazineSize());
		return magazineSize > 0 && stack.getOrDefault(ModComponents.AMMO, 0) <= 0;
	}

	/** Whether it can take on a target now. */
	public boolean canFight() {
		return this.post != null && !mustReturn() && !outOfAmmo() && HandheldWeaponItem.isWeapon(this.getMainHandItem());
	}

	public SoldierState state() {
		if (this.getTarget() != null) {
			return SoldierState.COMBAT;
		}
		if (mustReturn()) {
			return SoldierState.RETURNING;
		}
		if (outOfAmmo()) {
			return SoldierState.OUT_OF_AMMO;
		}
		return this.post != null && !this.post.route().isEmpty() ? SoldierState.PATROL : SoldierState.IDLE;
	}

	// ---------------------------------------------------------------- Soldier

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
		return false;
	}

	@Override
	public boolean mayFire() {
		return this.post != null && this.post.hasFood();
	}

	@Override
	public double engageRange() {
		return this.post != null ? this.post.engageRange() : SoldierPostBlockEntity.DEFAULT_ENGAGE_RANGE;
	}

	// ---------------------------------------------------------------- life and death

	@Override
	public void die(DamageSource damageSource) {
		super.die(damageSource);
		if (this.post != null) {
			this.post.onSoldierDied(this);
		}
	}

	/** Its gear belongs to the post - nothing drops. */
	@Override
	protected void dropCustomDeathLoot(ServerLevel world, DamageSource source, boolean causedByPlayer) {
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput view) {
		super.addAdditionalSaveData(view);
		view.store("post", BlockPos.CODEC, this.postPos);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput view) {
		super.readAdditionalSaveData(view);
		this.postPos = view.read("post", BlockPos.CODEC).orElse(BlockPos.ZERO);
	}
}
