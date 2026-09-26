package com.example.tudursguns.weapon;

import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.network.LockStatePayload;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursvehiclemod.asset.WeaponStats;
import com.example.tudursvehiclemod.asset.WeaponType;
import com.example.tudursvehiclemod.entity.WeaponTargeting;
import com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity;
import com.example.tudursvehiclemod.entity.projectile.WeaponProjectileFactory;
import com.example.tudursvehiclemod.network.WeaponFireSoundPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-side firing, reloading and lock-on for handheld weapons.
 *
 * The projectile itself, its guidance and the targeting math all come from Tudur's Vehicle Mod
 * (WeaponProjectileFactory / WeaponTargeting), so a weapon file behaves the same whether it's
 * mounted on a vehicle or carried by hand. What lives here is what a vehicle keeps on itself instead:
 * rate of fire, the loaded magazine, reloading from the player's inventory, and lock progress. */
public final class HandheldCombat {

	private HandheldCombat() {
	}

	/** Weapon types a handheld weapon can fire. The rest need a vehicle (CAS/Carrier/DropTank/
	 * Torpedo/Depth/ASWeapon), produce no projectile (Smoke/TargetingPod/Dummy) or aren't defined
	 * for a single shooter (Other). */
	public static final Set<WeaponType> SUPPORTED_TYPES = EnumSet.of(
			WeaponType.MACHINE_GUN, WeaponType.ROCKET, WeaponType.BOMB,
			WeaponType.AS_MISSILE, WeaponType.MK_ROCKET,
			WeaponType.AA_MISSILE, WeaponType.AT_MISSILE, WeaponType.MISSILE,
			WeaponType.TV_MISSILE, WeaponType.DISPENSER);

	/** Weapon types that must complete a lock before firing (same rule as on a vehicle). */
	public static boolean requiresLock(WeaponType type) {
		return type == WeaponType.AA_MISSILE || type == WeaponType.AT_MISSILE || type == WeaponType.MISSILE;
	}

	/** World time before which the player can't fire again (per player - a player only ever fires the
	 * weapon in hand, and switching weapons shouldn't reset the rate of fire). Not saved. */
	private static final Map<UUID, Long> NEXT_FIRE_TIME = new ConcurrentHashMap<>();
	private static final Map<UUID, LockState> LOCKS = new ConcurrentHashMap<>();

	private record LockState(int targetId, int progressTicks, int requiredTicks) {
	}

	public static void forget(UUID playerId) {
		NEXT_FIRE_TIME.remove(playerId);
		LOCKS.remove(playerId);
	}

	// ---------------------------------------------------------------- firing

	/** Tries to fire one shot. lockTarget is the locked entity for AA/AT/Missile (null otherwise).
	 * Returns true if a projectile was spawned. */
	public static boolean tryFire(ServerPlayerEntity player, ItemStack stack, Hand hand, HandheldDefinition def,
			WeaponStats stats, Entity lockTarget) {
		if (player.getVehicle() != null) {
			return false; // handheld weapons can't be used while riding anything
		}
		if (!SUPPORTED_TYPES.contains(stats.weaponType())) {
			player.sendMessage(Text.translatable("message.tudursguns.unsupported_type", stats.weaponType().name()), true);
			return false;
		}
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		long now = world.getTime();
		if (now < NEXT_FIRE_TIME.getOrDefault(player.getUuid(), Long.MIN_VALUE)) {
			return false;
		}
		if (isReloading(stack)) {
			return false;
		}
		int magazineSize = stats.magazineSize();
		if (magazineSize > 0 && stack.getOrDefault(ModComponents.AMMO, 0) <= 0) {
			startReload(player, stack, def, stats, true);
			return false;
		}

		int mode = stack.getOrDefault(ModComponents.MODE, 0);
		Item projectileItem = Registries.ITEM.get(def.projectileItem());
		VehicleProjectileEntity projectile = WeaponProjectileFactory.create(world, player, projectileItem.getDefaultStack(), stats, mode);

		Vec3d spawnPos = muzzlePosition(player, hand, def);
		projectile.setPosition(spawnPos.x, spawnPos.y, spawnPos.z);

		Vec3d velocity = player.getRotationVec(1.0f).multiply(stats.velocity());
		if (def.inheritShooterVelocity()) {
			velocity = velocity.add(player.getVelocity());
		}
		velocity = WeaponTargeting.applyAccuracySpread(velocity, stats.accuracyDegrees(), world.random);
		projectile.setVelocity(velocity);
		double speed = velocity.length();
		if (speed > 1.0E-6) {
			float pitch = (float) Math.toDegrees(-Math.asin(MathHelper.clamp(velocity.y / speed, -1.0, 1.0)));
			float yaw = (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
			projectile.setAngles(yaw, pitch);
		}

		// Guidance - the same per-type setup AbstractVehicleEntity.tryFireWeapon() does.
		switch (stats.weaponType()) {
			case AS_MISSILE, MK_ROCKET -> projectile.tudursvehiclemod$setGuidanceTargetPos(
					WeaponTargeting.raycastGroundPoint(world, player), stats.turnRateDegreesPerTick());
			case AA_MISSILE, AT_MISSILE, MISSILE -> {
				projectile.tudursvehiclemod$setMissileGuidanceTuning(stats.rigidityTimeTicks(), stats.proximityFuseDist());
				projectile.tudursvehiclemod$setTopAttack(stats.weaponType() == WeaponType.AT_MISSILE && stats.hasModes() && mode == 1);
				if (lockTarget != null) {
					projectile.tudursvehiclemod$setGuidanceTargetEntity(lockTarget.getId(), stats.turnRateDegreesPerTick());
				}
			}
			// Mode 0 is steered by the shooter (camera follows the missile); mode 1 homes on the
			// ground point under the crosshair instead.
			case TV_MISSILE -> {
				if (stats.hasModes() && mode == 1) {
					projectile.tudursvehiclemod$setGuidanceTargetPos(
							WeaponTargeting.raycastGroundPoint(world, player), stats.turnRateDegreesPerTick());
				} else {
					projectile.tudursvehiclemod$setTvControlled(player);
				}
			}
			default -> {
			}
		}

		world.spawnEntity(projectile);
		projectile.tudursvehiclemod$forceLoadSpawnChunk();

		NEXT_FIRE_TIME.put(player.getUuid(), now + Math.max(1, stats.cooldownTicks()));
		if (magazineSize > 0) {
			int remaining = stack.getOrDefault(ModComponents.AMMO, 0) - 1;
			stack.set(ModComponents.AMMO, Math.max(0, remaining));
			if (remaining <= 0) {
				startReload(player, stack, def, stats, false);
			}
		}
		playFireSound(player, stats, spawnPos);
		return true;
	}

	/** Eye position plus the definition's muzzle_offset (right, up, forward in the player's own view),
	 * mirrored for a left-side hand. Pulled back to the eye if a block is in the way, so a weapon
	 * held against a wall can't shoot through it. */
	private static Vec3d muzzlePosition(PlayerEntity player, Hand hand, HandheldDefinition def) {
		Vec3d eye = player.getEyePos();
		Vec3d forward = player.getRotationVec(1.0f);
		double yawRad = Math.toRadians(player.getYaw());
		Vec3d right = new Vec3d(-Math.cos(yawRad), 0.0, -Math.sin(yawRad));
		boolean rightSide = (hand == Hand.MAIN_HAND) == (player.getMainArm() == Arm.RIGHT);
		double side = rightSide ? def.muzzleOffset().x() : -def.muzzleOffset().x();
		Vec3d target = eye.add(right.multiply(side)).add(0.0, def.muzzleOffset().y(), 0.0)
				.add(forward.multiply(def.muzzleOffset().z()));
		BlockHitResult hit = player.getEntityWorld().raycast(new RaycastContext(eye, target,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? target : eye;
	}

	/** Same sound path as a vehicle weapon (WeaponFireSoundPayload, played by Tudur's Vehicle Mod's
	 * client), sent to everyone tracking the shooter plus the shooter. */
	private static void playFireSound(ServerPlayerEntity player, WeaponStats stats, Vec3d pos) {
		if (stats.sound().isEmpty()) {
			return;
		}
		WeaponFireSoundPayload payload = new WeaponFireSoundPayload(stats.sound().get(), pos.x, pos.y, pos.z,
				stats.soundVolume(), stats.soundPitch(), stats.soundPitchRandom());
		for (ServerPlayerEntity tracking : PlayerLookup.tracking(player)) {
			ServerPlayNetworking.send(tracking, payload);
		}
		ServerPlayNetworking.send(player, payload);
	}

	// ---------------------------------------------------------------- reloading

	public static boolean isReloading(ItemStack stack) {
		return stack.contains(ModComponents.RELOAD_UNTIL);
	}

	/** Starts a reload unless one is running, the magazine is full, or there's nothing to load.
	 * notifyIfEmpty shows "no ammo" when the player has none. */
	public static void startReload(ServerPlayerEntity player, ItemStack stack, HandheldDefinition def, WeaponStats stats,
			boolean notifyIfEmpty) {
		int magazineSize = stats.magazineSize();
		if (magazineSize <= 0 || isReloading(stack) || stack.getOrDefault(ModComponents.AMMO, 0) >= magazineSize) {
			return;
		}
		if (availableRounds(player, def) <= 0) {
			if (notifyIfEmpty) {
				player.sendMessage(Text.translatable("message.tudursguns.no_ammo"), true);
			}
			return;
		}
		long now = player.getEntityWorld().getTime();
		stack.set(ModComponents.RELOAD_UNTIL, now + Math.max(1, stats.reloadTicks()));
	}

	/** Finishes a reload whose time is up: loads as many rounds as the inventory can supply. */
	public static void tickReload(PlayerEntity player, ItemStack stack, HandheldDefinition def, WeaponStats stats) {
		Long until = stack.get(ModComponents.RELOAD_UNTIL);
		if (until == null || player.getEntityWorld().getTime() < until) {
			return;
		}
		stack.remove(ModComponents.RELOAD_UNTIL);
		int loaded = stack.getOrDefault(ModComponents.AMMO, 0);
		int wanted = Math.max(0, stats.magazineSize() - loaded);
		stack.set(ModComponents.AMMO, loaded + takeRounds(player, def, wanted));
	}

	/** Rounds the player could load right now. Unlimited without an ammo_item, or in creative mode. */
	public static int availableRounds(PlayerEntity player, HandheldDefinition def) {
		if (def.ammoItem().isEmpty() || player.isCreative()) {
			return Integer.MAX_VALUE;
		}
		Item ammo = Registries.ITEM.get(def.ammoItem().get());
		long rounds = (long) countItems(player, ammo) * def.roundsPerAmmoItem();
		return (int) Math.min(Integer.MAX_VALUE, rounds);
	}

	public static int countItems(PlayerEntity player, Item item) {
		int count = 0;
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack candidate = inventory.getStack(slot);
			if (candidate.isOf(item)) {
				count += candidate.getCount();
			}
		}
		return count;
	}

	/** Consumes ammo items for up to wanted rounds; returns the rounds actually loaded. One ammo item
	 * gives rounds_per_ammo_item rounds, so a partly used item's remainder is lost - like
	 * discarding a partly used magazine. */
	private static int takeRounds(PlayerEntity player, HandheldDefinition def, int wanted) {
		if (wanted <= 0) {
			return 0;
		}
		if (def.ammoItem().isEmpty() || player.isCreative()) {
			return wanted;
		}
		Item ammo = Registries.ITEM.get(def.ammoItem().get());
		int perItem = def.roundsPerAmmoItem();
		int itemsWanted = (wanted + perItem - 1) / perItem;
		int itemsTaken = 0;
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.size() && itemsTaken < itemsWanted; slot++) {
			ItemStack candidate = inventory.getStack(slot);
			if (candidate.isOf(ammo)) {
				int take = Math.min(candidate.getCount(), itemsWanted - itemsTaken);
				candidate.decrement(take);
				itemsTaken += take;
			}
		}
		return Math.min(wanted, itemsTaken * perItem);
	}

	// ---------------------------------------------------------------- modes

	/** Cycles the ModeNum mode (MachineGun HE rounds, Rocket bomblets, ATMissile top attack, TVMissile
	 * guided mode). Returns false if the weapon has only one mode. */
	public static boolean cycleMode(ServerPlayerEntity player, ItemStack stack, WeaponStats stats) {
		int modes = Math.max(1, stats.modeNum());
		if (modes < 2) {
			return false;
		}
		int next = (stack.getOrDefault(ModComponents.MODE, 0) + 1) % modes;
		stack.set(ModComponents.MODE, next);
		player.sendMessage(Text.translatable("message.tudursguns.mode", next + 1, modes), true);
		return true;
	}

	// ---------------------------------------------------------------- lock-on

	/** Called every tick while the use key is held with an AA/AT/Missile weapon: keeps the target
	 * under the crosshair and counts up while it stays the same, exactly like a vehicle's lock
	 * (LockTime plus LockTimePerBlock x distance; any change of target restarts the count). */
	public static void updateLock(ServerPlayerEntity player, WeaponStats stats) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		Entity target = WeaponTargeting.findLockOnTarget(world, player, stats.weaponType(), stats.lockRange(), null);
		LockState previous = LOCKS.get(player.getUuid());
		LockState next;
		if (target == null) {
			next = null;
		} else {
			double distance = target.getEntityPos().distanceTo(player.getEyePos());
			int required = (int) Math.round(stats.lockTimeTicks() + stats.lockTimePerBlock() * distance);
			int progress = previous != null && previous.targetId() == target.getId() ? previous.progressTicks() + 1 : 0;
			next = new LockState(target.getId(), progress, required);
		}
		setLock(player, next);
	}

	/** The completed lock's target, or null if the lock isn't complete (or the target is gone). */
	public static Entity completedLockTarget(ServerPlayerEntity player) {
		LockState state = LOCKS.get(player.getUuid());
		if (state == null || state.progressTicks() < state.requiredTicks()) {
			return null;
		}
		return player.getEntityWorld().getEntityById(state.targetId());
	}

	public static void clearLock(ServerPlayerEntity player) {
		setLock(player, null);
	}

	private static void setLock(ServerPlayerEntity player, LockState next) {
		LockState previous = next == null ? LOCKS.remove(player.getUuid()) : LOCKS.put(player.getUuid(), next);
		if (java.util.Objects.equals(previous, next)) {
			return;
		}
		ServerPlayNetworking.send(player, next == null ? LockStatePayload.NONE
				: new LockStatePayload(next.targetId(), next.progressTicks(), next.requiredTicks()));
	}

	public static boolean hasLockState(ServerPlayerEntity player) {
		return LOCKS.containsKey(player.getUuid());
	}
}
