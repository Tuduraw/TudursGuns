package com.example.tudursguns.entity;

import com.example.tudursguns.registry.ModEntityTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** An invisible, intangible stand-in for "the smoke" as a lock-on target - see SmokeCloudEntity.
 * A LivingEntity because Tudur's Vehicle Mod's lock-on only considers living entities and vehicles.
 * Nothing can hit, push or hurt it, and it removes itself as soon as its cloud is gone. */
public class SmokeDecoyEntity extends LivingEntity {

	private SmokeCloudEntity cloud;

	public SmokeDecoyEntity(EntityType<? extends LivingEntity> type, World world) {
		super(type, world);
		this.setInvisible(true);
		this.setNoGravity(true);
	}

	public static SmokeDecoyEntity create(ServerWorld world, SmokeCloudEntity cloud, Vec3d pos) {
		SmokeDecoyEntity decoy = new SmokeDecoyEntity(ModEntityTypes.SMOKE_DECOY, world);
		decoy.cloud = cloud;
		decoy.setPosition(pos.x, pos.y, pos.z);
		world.spawnEntity(decoy);
		return decoy;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return LivingEntity.createLivingAttributes()
				.add(EntityAttributes.MAX_HEALTH, 20.0)
				.add(EntityAttributes.MOVEMENT_SPEED, 0.0);
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.getEntityWorld().isClient() && (this.cloud == null || this.cloud.isRemoved())) {
			this.discard();
		}
	}

	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(ServerWorld world, DamageSource source) {
		return true;
	}

	@Override
	public boolean canHit() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void pushAwayFrom(Entity entity) {
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public boolean hasNoGravity() {
		return true;
	}

	@Override
	public boolean canMoveVoluntarily() {
		return false;
	}

	@Override
	public ItemStack getEquippedStack(EquipmentSlot slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public void equipStack(EquipmentSlot slot, ItemStack stack) {
	}

	@Override
	public Arm getMainArm() {
		return Arm.RIGHT;
	}
}
