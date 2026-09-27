package com.example.tudursguns.weapon;

import com.example.tudursguns.handheld.AnimationDefinition;
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
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
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
import java.util.Optional;
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
	/** World time of each player's last shot. Not saved. */
	private static final Map<UUID, Long> LAST_FIRED = new ConcurrentHashMap<>();

	/** How long after a shot a weapon still counts as in use (see isInAction). */
	public static final int ACTION_COOLDOWN_TICKS = 60;

	private record LockState(int targetId, int progressTicks, int requiredTicks) {
	}

	public static void forget(UUID playerId) {
		NEXT_FIRE_TIME.remove(playerId);
		LAST_FIRED.remove(playerId);
		RAISE_START.remove(playerId);
		PENDING_SHOTS.remove(playerId);
		LOCKS.remove(playerId);
		AIM_KEY_HELD.remove(playerId);
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
		WeaponModifiers modifiers = WeaponModifiers.of(stack, def);
		int magazineSize = modifiers.magazineSize(stats.magazineSize());
		if (magazineSize > 0 && stack.getOrDefault(Firing.ammoComponent(stack), 0) <= 0) {
			WeaponAnimationEvents.trigger(stack, world, WeaponAnimationEvents.forSelection(stack, AnimationDefinition.Event.EMPTY));
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
		velocity = WeaponTargeting.applyAccuracySpread(velocity, stats.accuracyDegrees() * modifiers.accuracyMultiplier(), world.random);
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
		stack.set(ModComponents.COOLDOWN_UNTIL, now + Math.max(1, stats.cooldownTicks()));
		LAST_FIRED.put(player.getUuid(), now);
		WeaponAnimationEvents.trigger(stack, world, WeaponAnimationEvents.forSelection(stack, AnimationDefinition.Event.FIRE));
		if (magazineSize > 0) {
			int remaining = stack.getOrDefault(Firing.ammoComponent(stack), 0) - 1;
			stack.set(Firing.ammoComponent(stack), Math.max(0, remaining));
			if (remaining <= 0) {
				startReload(player, stack, def, stats, false);
			}
		}
		playFireSound(player, stats, modifiers, spawnPos);
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
	 * client), sent to everyone tracking the shooter plus the shooter. Attachments can replace the
	 * sound (a silencer) and scale its volume - which also scales how far it carries - and pitch. */
	private static void playFireSound(ServerPlayerEntity player, WeaponStats stats, WeaponModifiers modifiers, Vec3d pos) {
		Optional<String> sound = modifiers.soundOverride().isPresent() ? modifiers.soundOverride() : stats.sound();
		if (sound.isEmpty()) {
			return;
		}
		playSound(player, sound.get(), pos, stats.soundVolume() * modifiers.volumeMultiplier(),
				stats.soundPitch() * modifiers.pitchMultiplier(), stats.soundPitchRandom());
	}

	/** Plays a named sound (any .ogg Tudur's Vehicle Mod's sound loader knows) at pos for everyone
	 * tracking the player plus the player. */
	public static void playSound(ServerPlayerEntity player, String sound, Vec3d pos, float volume, float pitch, float pitchRandom) {
		WeaponFireSoundPayload payload = new WeaponFireSoundPayload(sound, pos.x, pos.y, pos.z, volume, pitch, pitchRandom);
		for (ServerPlayerEntity tracking : PlayerLookup.tracking(player)) {
			ServerPlayNetworking.send(tracking, payload);
		}
		ServerPlayNetworking.send(player, payload);
	}

	/** Plays a named sound at pos for every player within earshot (scaled by volume, like the vehicle
	 * mod's own weapon sounds) - for sounds with no player behind them (a mine, a grenade's effect). */
	public static void playSoundAt(ServerWorld world, String sound, Vec3d pos, float volume, float pitch, float pitchRandom) {
		WeaponFireSoundPayload payload = new WeaponFireSoundPayload(sound, pos.x, pos.y, pos.z, volume, pitch, pitchRandom);
		for (ServerPlayerEntity listener : PlayerLookup.around(world, pos, Math.max(16.0, 16.0 * volume))) {
			ServerPlayNetworking.send(listener, payload);
		}
	}

	// ---------------------------------------------------------------- reloading

	public static boolean isReloading(ItemStack stack) {
		return stack.contains(ModComponents.RELOAD_UNTIL);
	}

	/** Starts a reload unless one is running, the magazine is full, or there's nothing to load.
	 * notifyIfEmpty shows "no ammo" when the player has none. */
	public static void startReload(ServerPlayerEntity player, ItemStack stack, HandheldDefinition def, WeaponStats stats,
			boolean notifyIfEmpty) {
		WeaponModifiers modifiers = WeaponModifiers.of(stack, def);
		int magazineSize = modifiers.magazineSize(stats.magazineSize());
		if (magazineSize <= 0 || isReloading(stack) || stack.getOrDefault(Firing.ammoComponent(stack), 0) >= magazineSize) {
			return;
		}
		if (availableRounds(player, def) <= 0) {
			if (notifyIfEmpty) {
				player.sendMessage(Text.translatable("message.tudursguns.no_ammo"), true);
			}
			return;
		}
		long now = player.getEntityWorld().getTime();
		int reloadTicks = modifiers.reloadTicks(stats.reloadTicks());
		stack.set(ModComponents.RELOAD_UNTIL, now + reloadTicks);
		WeaponAnimationEvents.trigger(stack, player.getEntityWorld(),
				WeaponAnimationEvents.forSelection(stack, AnimationDefinition.Event.RELOAD), reloadTicks);
		def.reloadSound().ifPresent(sound -> playSound(player, sound, player.getEyePos(), 1.0f, 1.0f, 0.05f));
	}

	/** Finishes a reload whose time is up: loads as many rounds as the inventory can supply. */
	public static void tickReload(PlayerEntity player, ItemStack stack, HandheldDefinition def, WeaponStats stats) {
		Long until = stack.get(ModComponents.RELOAD_UNTIL);
		if (until == null || player.getEntityWorld().getTime() < until) {
			return;
		}
		stack.remove(ModComponents.RELOAD_UNTIL);
		int loaded = stack.getOrDefault(Firing.ammoComponent(stack), 0);
		int wanted = Math.max(0, WeaponModifiers.of(stack, def).magazineSize(stats.magazineSize()) - loaded);
		stack.set(Firing.ammoComponent(stack), loaded + takeRounds(player, def, wanted));
		WeaponAnimationEvents.trigger(stack, player.getEntityWorld(),
				WeaponAnimationEvents.forSelection(stack, AnimationDefinition.Event.RELOAD_END));
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

	// ---------------------------------------------------------------- attachments

	private static final net.minecraft.util.Identifier MELEE_BONUS_MODIFIER_ID =
			net.minecraft.util.Identifier.of(com.example.tudursguns.TudursGuns.MOD_ID, "attachment_melee_bonus");

	/** Re-applies everything that follows from the fitted attachments after they change: loaded
	 * rounds beyond the new magazine size are removed (taking off an extended magazine takes its
	 * rounds with it), and melee damage is set from the bayonet-style bonuses. */
	public static void applyAttachmentEffects(ItemStack stack, HandheldDefinition def, WeaponStats stats) {
		if (Firing.underbarrel(stack, def) == null) {
			// Launcher taken off: its loaded rounds go with it.
			stack.remove(ModComponents.ALT_SELECTED);
			stack.remove(ModComponents.ALT_AMMO);
		}
		WeaponModifiers modifiers = WeaponModifiers.of(stack, def);
		int magazineSize = modifiers.magazineSize(stats.magazineSize());
		if (magazineSize > 0 && stack.getOrDefault(ModComponents.AMMO, 0) > magazineSize) {
			stack.set(ModComponents.AMMO, magazineSize);
		}
		if (modifiers.meleeDamageBonus() > 0f) {
			stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.builder()
					.add(EntityAttributes.ATTACK_DAMAGE,
							new EntityAttributeModifier(MELEE_BONUS_MODIFIER_ID, modifiers.meleeDamageBonus(),
									EntityAttributeModifier.Operation.ADD_VALUE),
							AttributeModifierSlot.MAINHAND)
					.build());
		} else {
			stack.remove(DataComponentTypes.ATTRIBUTE_MODIFIERS);
		}
	}

	// ---------------------------------------------------------------- aiming

	/** Players holding the aim key (reported by their client). Holding use also aims, but that's
	 * already visible to everyone through the vanilla "using item" state. Not saved. */
	private static final Set<UUID> AIM_KEY_HELD = ConcurrentHashMap.newKeySet();

	/** Records the player's aim key state; returns true if it changed. */
	public static boolean setAimKeyHeld(ServerPlayerEntity player, boolean held) {
		boolean changed = held ? AIM_KEY_HELD.add(player.getUuid()) : AIM_KEY_HELD.remove(player.getUuid());
		if (held) {
			startRaising(player);
		} else {
			stopRaisingUnlessAiming(player);
		}
		return changed;
	}

	// ---------------------------------------------------------------- raising

	/** World time the player started raising their weapon (aim key or use), while it's up. */
	private static final Map<UUID, Long> RAISE_START = new ConcurrentHashMap<>();

	/** A shot asked for with use from the hip, waiting for the weapon to be fully up. */
	private record PendingShot(Hand hand, long requestedAt) {
	}

	private static final Map<UUID, PendingShot> PENDING_SHOTS = new ConcurrentHashMap<>();

	/** A pending shot not fired within this long after it was asked for is dropped. */
	private static final int PENDING_SHOT_TIMEOUT_TICKS = 40;

	public static void startRaising(ServerPlayerEntity player) {
		RAISE_START.putIfAbsent(player.getUuid(), player.getEntityWorld().getTime());
	}

	/** Use pressed: raising starts now - unless the weapon is already up (aim key) or on its way up
	 * for a shot already waiting. */
	public static void startRaisingForUse(ServerPlayerEntity player) {
		if (isAimKeyHeld(player) || PENDING_SHOTS.containsKey(player.getUuid())) {
			startRaising(player);
		} else {
			RAISE_START.put(player.getUuid(), player.getEntityWorld().getTime());
		}
	}

	/** The weapon comes down unless the aim key still holds it up, or a shot is still waiting to go. */
	public static void stopRaisingUnlessAiming(ServerPlayerEntity player) {
		if (!isAimKeyHeld(player) && !PENDING_SHOTS.containsKey(player.getUuid())) {
			RAISE_START.remove(player.getUuid());
		}
	}

	/** Whether the weapon is fully up: raising began at least raise_ticks ago. */
	public static boolean isRaised(ServerPlayerEntity player, HandheldDefinition def) {
		int raiseTicks = def.raiseTicks();
		if (raiseTicks <= 0) {
			return true;
		}
		Long start = RAISE_START.get(player.getUuid());
		return start != null && player.getEntityWorld().getTime() - start >= raiseTicks;
	}

	/** Whether the weapon is in its fire delay (as recorded on the stack - the client can see it too). */
	public static boolean isCoolingDown(ItemStack stack, long now) {
		Long until = stack.get(ModComponents.COOLDOWN_UNTIL);
		return until != null && now < until;
	}

	public static void requestShot(ServerPlayerEntity player, Hand hand) {
		PENDING_SHOTS.put(player.getUuid(), new PendingShot(hand, player.getEntityWorld().getTime()));
	}

	/** Fires a waiting shot once the weapon is up (even if use was already released - a quick click
	 * from the hip still fires, after the raise). Called every tick for the weapon in the player's
	 * inventory. */
	public static void tickPendingShot(ServerPlayerEntity player, ItemStack stack, HandheldDefinition base, Firing firing) {
		PendingShot pending = PENDING_SHOTS.get(player.getUuid());
		if (pending == null || player.getStackInHand(pending.hand()) != stack) {
			return;
		}
		if (isRaised(player, base)) {
			PENDING_SHOTS.remove(player.getUuid());
			tryFire(player, stack, pending.hand(), firing.definition(), firing.stats(), null);
			if (!(player.isUsingItem() && player.getActiveItem() == stack)) {
				stopRaisingUnlessAiming(player);
			}
		}
	}

	/** Housekeeping every tick: drops a waiting shot that timed out or whose weapon left the hand, and
	 * forgets the raise once nothing holds the weapon up (the item was switched while in use). */
	public static void tickRaiseState(ServerPlayerEntity player) {
		PendingShot pending = PENDING_SHOTS.get(player.getUuid());
		if (pending != null && (player.getEntityWorld().getTime() - pending.requestedAt() > PENDING_SHOT_TIMEOUT_TICKS
				|| player.getVehicle() != null
				|| !(player.getStackInHand(pending.hand()).getItem() instanceof com.example.tudursguns.item.HandheldWeaponItem))) {
			PENDING_SHOTS.remove(player.getUuid());
		}
		boolean usingWeapon = player.isUsingItem() && player.getActiveItem().getItem() instanceof com.example.tudursguns.item.HandheldWeaponItem;
		if (!usingWeapon) {
			stopRaisingUnlessAiming(player);
		}
	}

	public static boolean isAimKeyHeld(ServerPlayerEntity player) {
		return AIM_KEY_HELD.contains(player.getUuid());
	}

	/** True while the player is fighting with a weapon: holding use (firing, locking, aiming) or the
	 * aim key, or within ACTION_COOLDOWN_TICKS of their last shot. Resupply (the ammo box) waits until
	 * the weapon has been lowered, so it can't turn into an endless magazine in the middle of a fight. */
	public static boolean isInAction(ServerPlayerEntity player) {
		if (player.isUsingItem() || isAimKeyHeld(player)) {
			return true;
		}
		Long last = LAST_FIRED.get(player.getUuid());
		return last != null && player.getEntityWorld().getTime() - last < ACTION_COOLDOWN_TICKS;
	}

	// ---------------------------------------------------------------- modes

	/** Switches between the weapon's own fire and its underbarrel launcher. Returns false if no
	 * launcher is fitted. A reload in progress is cancelled (it was loading the other one). */
	public static boolean toggleUnderbarrel(ServerPlayerEntity player, ItemStack stack, HandheldDefinition def) {
		if (Firing.underbarrel(stack, def) == null) {
			stack.remove(ModComponents.ALT_SELECTED);
			return false;
		}
		boolean selected = !stack.getOrDefault(ModComponents.ALT_SELECTED, false);
		if (selected) {
			stack.set(ModComponents.ALT_SELECTED, true);
		} else {
			stack.remove(ModComponents.ALT_SELECTED);
		}
		stack.remove(ModComponents.RELOAD_UNTIL);
		clearLock(player);
		WeaponAnimationEvents.trigger(stack, player.getEntityWorld(), AnimationDefinition.Event.UNDERBARREL_SWITCH);
		player.sendMessage(Text.translatable(selected ? "message.tudursguns.underbarrel.on" : "message.tudursguns.underbarrel.off"), true);
		return true;
	}

	/** Cycles the ModeNum mode (MachineGun HE rounds, Rocket bomblets, ATMissile top attack, TVMissile
	 * guided mode). Returns false if the weapon has only one mode. */
	public static boolean cycleMode(ServerPlayerEntity player, ItemStack stack, WeaponStats stats) {
		int modes = Math.max(1, stats.modeNum());
		if (modes < 2) {
			return false;
		}
		int next = (stack.getOrDefault(ModComponents.MODE, 0) + 1) % modes;
		stack.set(ModComponents.MODE, next);
		WeaponAnimationEvents.trigger(stack, player.getEntityWorld(), AnimationDefinition.Event.MODE);
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
