package com.example.tudursguns.entity;

import com.example.tudursguns.registry.ModEntityTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** An invisible, intangible stand-in for "the smoke" as a lock-on target - see SmokeCloudEntity.
 * A LivingEntity because Tudur's Vehicle Mod's lock-on only considers living entities and vehicles.
 * Nothing can hit, push or hurt it, and it removes itself as soon as its cloud is gone. */
public class SmokeDecoyEntity extends LivingEntity implements MarkerEntity {

	private SmokeCloudEntity cloud;

	public SmokeDecoyEntity(EntityType<? extends LivingEntity> type, Level world) {
		super(type, world);
		this.setInvisible(true);
		this.setNoGravity(true);
	}

	public static SmokeDecoyEntity create(ServerLevel world, SmokeCloudEntity cloud, Vec3 pos) {
		SmokeDecoyEntity decoy = new SmokeDecoyEntity(ModEntityTypes.SMOKE_DECOY, world);
		decoy.cloud = cloud;
		decoy.setPos(pos.x, pos.y, pos.z);
		world.addFreshEntity(decoy);
		return decoy;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return LivingEntity.createLivingAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0);
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide() && isOrphaned()) {
			this.discard();
		}
	}

	/** Server side: true once whatever this stands in for is gone. */
	protected boolean isOrphaned() {
		return this.cloud == null || this.cloud.isRemoved();
	}

	@Override
	public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel world, DamageSource source) {
		return true;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void push(Entity entity) {
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	public boolean canSimulateMovement() {
		return false;
	}

	@Override
	public ItemStack getItemBySlot(EquipmentSlot slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
	}

	@Override
	public HumanoidArm getMainArm() {
		return HumanoidArm.RIGHT;
	}
}
