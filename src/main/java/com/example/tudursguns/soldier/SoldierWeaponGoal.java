package com.example.tudursguns.soldier;

import com.example.tudursguns.handheld.AnimationDefinition;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.item.HandheldWeaponItem;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.weapon.FiringEffects;
import com.example.tudursguns.weapon.HandheldCombat;
import com.example.tudursguns.weapon.WeaponAnimationEvents;
import com.example.tudursguns.weapon.WeaponModifiers;
import com.example.tudursvehiclemod.asset.WeaponStats;
import com.example.tudursvehiclemod.asset.WeaponStatsLoader;
import java.util.EnumSet;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;

/** Fights the mob's target with the weapon in its main hand, as a player would: closes in until it
 * can see the target within engage range, raises the weapon, aims (allowing for the round's drop),
 * then fires at the weapon's own rate - automatic weapons in short bursts, semi-automatic ones with
 * a moment between shots, lock-on weapons once the lock is complete. Keeps its distance from a target
 * too close for an explosive round. */
public class SoldierWeaponGoal<T extends PathfinderMob & Soldier> extends Goal {

	/** Extra spread (degrees) on top of the weapon's own - a soldier isn't a perfect shot. */
	private static final float AIM_ERROR_DEGREES = 1.5f;
	/** Closest a target may be before an explosive round is fired at it. */
	private static final double EXPLOSIVE_MIN_DISTANCE = 6.0;

	private final T soldier;
	private final double moveSpeed;
	private long nextShotTime;
	private int burstLeft;
	private int lockTicks;

	public SoldierWeaponGoal(T soldier, double moveSpeed) {
		this.soldier = soldier;
		this.moveSpeed = moveSpeed;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = this.soldier.getTarget();
		return target != null && target.isAlive() && HandheldWeaponItem.isWeapon(this.soldier.getMainHandItem());
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void stop() {
		this.soldier.setAimingWeapon(false);
		this.soldier.getNavigation().stop();
		this.lockTicks = 0;
		this.burstLeft = 0;
	}

	@Override
	public void tick() {
		LivingEntity target = this.soldier.getTarget();
		ItemStack stack = this.soldier.getMainHandItem();
		HandheldDefinition def = HandheldWeaponItem.serverDefinition(stack);
		if (target == null || def == null) {
			return;
		}
		WeaponStats stats = WeaponStatsLoader.get(def.weapon());
		this.soldier.getLookControl().setLookAt(target, 30f, 30f);
		double distance = this.soldier.distanceTo(target);
		boolean visible = this.soldier.hasLineOfSight(target);
		boolean explosive = stats.explosionPower() > 0f;

		if (!visible || distance > this.soldier.engageRange()) {
			this.soldier.setAimingWeapon(false);
			this.lockTicks = 0;
			this.soldier.getNavigation().moveTo(target, this.moveSpeed);
			return;
		}
		if (explosive && distance < EXPLOSIVE_MIN_DISTANCE) {
			// Back off rather than blow itself up.
			Vec3 away = this.soldier.position().subtract(target.position()).normalize().scale(4.0);
			Vec3 to = this.soldier.position().add(away);
			this.soldier.getNavigation().moveTo(to.x, to.y, to.z, this.moveSpeed);
			this.soldier.setAimingWeapon(false);
			return;
		}
		this.soldier.getNavigation().stop();
		this.soldier.setAimingWeapon(true);
		faceTowards(target);

		long now = this.soldier.level().getGameTime();
		if (tickReload(stack, def, stats, now)) {
			return;
		}
		WeaponModifiers modifiers = WeaponModifiers.of(stack, def);
		int magazineSize = modifiers.magazineSize(stats.magazineSize());
		if (magazineSize > 0 && stack.getOrDefault(ModComponents.AMMO, 0) <= 0) {
			if (this.soldier.hasUnlimitedAmmo()) {
				startReload(stack, def, stats, modifiers, now);
			}
			return;
		}
		if (!this.soldier.mayFire() || !HandheldCombat.isSupported(stats.weaponType()) || now < this.nextShotTime) {
			return;
		}
		LivingEntity lockTarget = null;
		if (HandheldCombat.requiresLock(stats.weaponType())) {
			this.lockTicks++;
			if (this.lockTicks < stats.lockTimeTicks() + stats.lockTimePerBlock() * distance) {
				return;
			}
			lockTarget = target;
			this.lockTicks = 0;
		}
		fire(stack, def, stats, modifiers, target, lockTarget);
		scheduleNextShot(def, stats, now);
	}

	private void fire(ItemStack stack, HandheldDefinition def, WeaponStats stats, WeaponModifiers modifiers, LivingEntity target,
			LivingEntity lockTarget) {
		Vec3 eye = this.soldier.getEyePosition();
		Vec3 aimPoint = target.getBoundingBox().getCenter().add(0.0, target.getBbHeight() * 0.15, 0.0);
		// Aim above the target by how far the round drops on the way (drag ignored).
		double horizontal = Math.sqrt(Mth.square(aimPoint.x - eye.x) + Mth.square(aimPoint.z - eye.z));
		double time = stats.velocity() > 0f ? horizontal / stats.velocity() : 0.0;
		Vec3 direction = aimPoint.add(0.0, 0.5 * stats.gravity() * time * time, 0.0).subtract(eye).normalize();
		Vec3 right = new Vec3(-direction.z, 0.0, direction.x).normalize();
		Vec3 muzzle = eye.add(direction.scale(0.6)).add(right.scale(0.25)).add(0.0, -0.1, 0.0);
		if (this.soldier.level().clip(new ClipContext(eye, muzzle, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, this.soldier)).getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
			muzzle = eye;
		}
		float spread = stats.accuracyDegrees() * modifiers.accuracyMultiplier() + AIM_ERROR_DEGREES;
		HandheldCombat.shoot(this.soldier, stack, InteractionHand.MAIN_HAND, def, stats, modifiers, muzzle, direction, spread, lockTarget,
				target.position());
	}

	/** Automatic weapons fire bursts of 3-6 rounds, then pause; burst weapons their burst_count; the rest
	 * take a moment between shots. */
	private void scheduleNextShot(HandheldDefinition def, WeaponStats stats, long now) {
		int delay = Math.max(1, stats.cooldownTicks());
		var random = this.soldier.getRandom();
		switch (def.fireMode()) {
			case AUTO, BURST -> {
				if (this.burstLeft <= 0) {
					this.burstLeft = def.fireMode() == HandheldDefinition.FireMode.BURST ? def.handling().burstCount() : 3 + random.nextInt(4);
				}
				this.burstLeft--;
				this.nextShotTime = now + delay + (this.burstLeft <= 0 ? 15 + random.nextInt(20) : 0);
			}
			case SEMI -> this.nextShotTime = now + delay + 4 + random.nextInt(8);
		}
	}

	/** Turns body and head to the target, so the shot, its effects and the pose all agree. */
	private void faceTowards(LivingEntity target) {
		Vec3 delta = target.getEyePosition().subtract(this.soldier.getEyePosition());
		float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
		float pitch = (float) Math.toDegrees(-Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
		this.soldier.setYRot(yaw);
		this.soldier.setYBodyRot(yaw);
		this.soldier.setYHeadRot(yaw);
		this.soldier.setXRot(pitch);
	}

	// ---------------------------------------------------------------- reloading (endless ammo)

	/** True while a reload is running; fills the magazine when it's done. */
	private boolean tickReload(ItemStack stack, HandheldDefinition def, WeaponStats stats, long now) {
		Long until = stack.get(ModComponents.RELOAD_UNTIL);
		if (until == null) {
			return false;
		}
		if (now < until) {
			return true;
		}
		stack.remove(ModComponents.RELOAD_UNTIL);
		stack.set(ModComponents.AMMO, WeaponModifiers.of(stack, def).magazineSize(stats.magazineSize()));
		WeaponAnimationEvents.trigger(this.soldier, stack, AnimationDefinition.Event.RELOAD_END);
		return false;
	}

	private void startReload(ItemStack stack, HandheldDefinition def, WeaponStats stats, WeaponModifiers modifiers, long now) {
		int reloadTicks = modifiers.reloadTicks(stats.reloadTicks());
		FiringEffects.onReload(this.soldier, stack, def, stats, modifiers.magazineSize(stats.magazineSize()));
		stack.set(ModComponents.RELOAD_UNTIL, now + reloadTicks);
		WeaponAnimationEvents.trigger(this.soldier, stack, AnimationDefinition.Event.RELOAD, reloadTicks);
		def.reloadSound().ifPresent(sound -> HandheldCombat.playSound(this.soldier, sound, this.soldier.getEyePosition(), 1.0f, 1.0f, 0.05f));
	}
}
