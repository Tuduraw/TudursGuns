package com.example.tudursguns.soldier;

import com.example.tudursguns.block.SoldierPostBlockEntity;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.item.HandheldWeaponItem;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.weapon.WeaponModifiers;
import com.example.tudursvehiclemod.asset.WeaponStatsLoader;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** The soldier a soldier post sends out (SoldierPostBlockEntity). It wears and carries what is in the
 * post's slots - the very same stacks, so rounds fired and armor worn down show in the post, and
 * taking a piece out of the post takes it off the soldier. It walks the post's route
 * (SoldierPatrolGoal), fights monsters near the route (SoldierTargetGoal), goes back to the post
 * to wait for an ammo box when its magazine is empty, and walks back when the post runs out of food
 * or is stood down (then it goes into the post). It disappears if its post is gone. */
public class FriendlySoldierEntity extends PathAwareEntity implements Soldier {

	private static final TrackedData<Boolean> AIMING = DataTracker.registerData(FriendlySoldierEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final int HEAL_INTERVAL_TICKS = 40;

	private BlockPos postPos = BlockPos.ORIGIN;
	/** The post, looked up once a tick (null while it isn't loaded). */
	private SoldierPostBlockEntity post;

	public FriendlySoldierEntity(EntityType<? extends FriendlySoldierEntity> type, World world) {
		super(type, world);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			this.setEquipmentDropChance(slot, 0f);
		}
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.MAX_HEALTH, 20.0)
				.add(EntityAttributes.MOVEMENT_SPEED, 0.25)
				.add(EntityAttributes.FOLLOW_RANGE, 64.0);
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
		this.goalSelector.add(4, new SoldierPatrolGoal(this, 0.8));
		this.goalSelector.add(7, new LookAroundGoal(this));
		this.targetSelector.add(1, new SoldierTargetGoal(this));
	}

	public void setPost(BlockPos pos) {
		this.postPos = pos.toImmutable();
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
		if (this.getEntityWorld() instanceof ServerWorld world && !this.isRemoved()) {
			this.post = null;
			if (world.isPosLoaded(this.postPos)) {
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
	protected void mobTick(ServerWorld world) {
		super.mobTick(world);
		if (this.age % HEAL_INTERVAL_TICKS == 0 && this.getTarget() == null && this.post != null && this.post.hasFood()
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
		ItemStack stack = this.getMainHandStack();
		HandheldDefinition def = HandheldWeaponItem.serverDefinition(stack);
		if (def == null) {
			return false;
		}
		int magazineSize = WeaponModifiers.of(stack, def).magazineSize(WeaponStatsLoader.get(def.weapon()).magazineSize());
		return magazineSize > 0 && stack.getOrDefault(ModComponents.AMMO, 0) <= 0;
	}

	/** Whether it can take on a target now. */
	public boolean canFight() {
		return this.post != null && !mustReturn() && !outOfAmmo() && HandheldWeaponItem.isWeapon(this.getMainHandStack());
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
		return this.dataTracker.get(AIMING);
	}

	@Override
	public void setAimingWeapon(boolean aiming) {
		this.dataTracker.set(AIMING, aiming);
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
	public void onDeath(DamageSource damageSource) {
		super.onDeath(damageSource);
		if (this.post != null) {
			this.post.onSoldierDied(this);
		}
	}

	/** Its gear belongs to the post - nothing drops. */
	@Override
	protected void dropEquipment(ServerWorld world, DamageSource source, boolean causedByPlayer) {
	}

	@Override
	public boolean cannotDespawn() {
		return true;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	protected void writeCustomData(WriteView view) {
		super.writeCustomData(view);
		view.put("post", BlockPos.CODEC, this.postPos);
	}

	@Override
	protected void readCustomData(ReadView view) {
		super.readCustomData(view);
		this.postPos = view.read("post", BlockPos.CODEC).orElse(BlockPos.ORIGIN);
	}
}
