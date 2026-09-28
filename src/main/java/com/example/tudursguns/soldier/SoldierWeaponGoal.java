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
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.EnumSet;

/** Fights the mob's target with the weapon in its main hand, as a player would: closes in until it
 * can see the target within engage range, raises the weapon, aims (allowing for the round's drop),
 * then fires at the weapon's own rate - automatic weapons in short bursts, semi-automatic ones with
 * a moment between shots, lock-on weapons once the lock is complete. Keeps its distance from a target
 * too close for an explosive round. */
public class SoldierWeaponGoal<T extends PathAwareEntity & Soldier> extends Goal {

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
		this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
	}

	@Override
	public boolean canStart() {
		LivingEntity target = this.soldier.getTarget();
		return target != null && target.isAlive() && HandheldWeaponItem.isWeapon(this.soldier.getMainHandStack());
	}

	@Override
	public boolean shouldContinue() {
		return canStart();
	}

	@Override
	public boolean shouldRunEveryTick() {
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
		ItemStack stack = this.soldier.getMainHandStack();
		HandheldDefinition def = HandheldWeaponItem.serverDefinition(stack);
		if (target == null || def == null) {
			return;
		}
		WeaponStats stats = WeaponStatsLoader.get(def.weapon());
		this.soldier.getLookControl().lookAt(target, 30f, 30f);
		double distance = this.soldier.distanceTo(target);
		boolean visible = this.soldier.canSee(target);
		boolean explosive = stats.explosionPower() > 0f;

		if (!visible || distance > this.soldier.engageRange()) {
			this.soldier.setAimingWeapon(false);
			this.lockTicks = 0;
			this.soldier.getNavigation().startMovingTo(target, this.moveSpeed);
			return;
		}
		if (explosive && distance < EXPLOSIVE_MIN_DISTANCE) {
			// Back off rather than blow itself up.
			Vec3d away = this.soldier.getEntityPos().subtract(target.getEntityPos()).normalize().multiply(4.0);
			Vec3d to = this.soldier.getEntityPos().add(away);
			this.soldier.getNavigation().startMovingTo(to.x, to.y, to.z, this.moveSpeed);
			this.soldier.setAimingWeapon(false);
			return;
		}
		this.soldier.getNavigation().stop();
		this.soldier.setAimingWeapon(true);
		faceTowards(target);

		long now = this.soldier.getEntityWorld().getTime();
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
		Vec3d eye = this.soldier.getEyePos();
		Vec3d aimPoint = target.getBoundingBox().getCenter().add(0.0, target.getHeight() * 0.15, 0.0);
		// Aim above the target by how far the round drops on the way (drag ignored).
		double horizontal = Math.sqrt(MathHelper.square(aimPoint.x - eye.x) + MathHelper.square(aimPoint.z - eye.z));
		double time = stats.velocity() > 0f ? horizontal / stats.velocity() : 0.0;
		Vec3d direction = aimPoint.add(0.0, 0.5 * stats.gravity() * time * time, 0.0).subtract(eye).normalize();
		Vec3d right = new Vec3d(-direction.z, 0.0, direction.x).normalize();
		Vec3d muzzle = eye.add(direction.multiply(0.6)).add(right.multiply(0.25)).add(0.0, -0.1, 0.0);
		if (this.soldier.getEntityWorld().raycast(new RaycastContext(eye, muzzle, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, this.soldier)).getType() != net.minecraft.util.hit.HitResult.Type.MISS) {
			muzzle = eye;
		}
		float spread = stats.accuracyDegrees() * modifiers.accuracyMultiplier() + AIM_ERROR_DEGREES;
		HandheldCombat.shoot(this.soldier, stack, Hand.MAIN_HAND, def, stats, modifiers, muzzle, direction, spread, lockTarget,
				target.getEntityPos());
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
		Vec3d delta = target.getEyePos().subtract(this.soldier.getEyePos());
		float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
		float pitch = (float) Math.toDegrees(-Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
		this.soldier.setYaw(yaw);
		this.soldier.setBodyYaw(yaw);
		this.soldier.setHeadYaw(yaw);
		this.soldier.setPitch(pitch);
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
		def.reloadSound().ifPresent(sound -> HandheldCombat.playSound(this.soldier, sound, this.soldier.getEyePos(), 1.0f, 1.0f, 0.05f));
	}
}
