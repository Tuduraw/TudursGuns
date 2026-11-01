package com.example.tudursguns.weapon;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.handheld.AnimationDefinition;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.item.HandheldWeaponItem;
import com.example.tudursguns.network.LockStatePayload;
import com.example.tudursguns.network.RecoilPayload;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursvehiclemod.asset.WeaponStats;
import com.example.tudursvehiclemod.asset.WeaponType;
import com.example.tudursvehiclemod.entity.WeaponTargeting;
import com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity;
import com.example.tudursvehiclemod.entity.projectile.WeaponProjectileFactory;
import com.example.tudursvehiclemod.network.WeaponFireSoundPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
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
	private static final Set<WeaponType> SUPPORTED_TYPES = EnumSet.of(
			WeaponType.MACHINE_GUN, WeaponType.ROCKET, WeaponType.BOMB,
			WeaponType.AS_MISSILE, WeaponType.MK_ROCKET,
			WeaponType.AA_MISSILE, WeaponType.AT_MISSILE, WeaponType.MISSILE,
			WeaponType.TV_MISSILE, WeaponType.DISPENSER);

	/** Whether a handheld weapon can fire this weapon type at all. */
	public static boolean isSupported(WeaponType type) {
		return SUPPORTED_TYPES.contains(type);
	}

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
	private static final int ACTION_COOLDOWN_TICKS = 60;

	private record LockState(int targetId, int progressTicks, int requiredTicks) {
	}

	public static void forget(UUID playerId) {
		NEXT_FIRE_TIME.remove(playerId);
		LAST_FIRED.remove(playerId);
		RAISE_START.remove(playerId);
		PENDING_SHOTS.remove(playerId);
		BURSTS.remove(playerId);
		LOCKS.remove(playerId);
		AIM_KEY_HELD.remove(playerId);
	}

	// ---------------------------------------------------------------- firing

	/** Tries to fire one shot. lockTarget is the locked entity for AA/AT/Missile (null otherwise).
	 * Returns true if a projectile was spawned. */
	public static boolean tryFire(ServerPlayer player, ItemStack stack, InteractionHand hand, HandheldDefinition def,
			WeaponStats stats, Entity lockTarget) {
		if (player.getVehicle() != null) {
			return false; // handheld weapons can't be used while riding anything
		}
		if (!SUPPORTED_TYPES.contains(stats.weaponType())) {
			player.displayClientMessage(Component.translatable("message.tudursguns.unsupported_type", stats.weaponType().name()), true);
			return false;
		}
		ServerLevel world = (ServerLevel) player.level();
		long now = world.getGameTime();
		if (now < NEXT_FIRE_TIME.getOrDefault(player.getUUID(), Long.MIN_VALUE)) {
			return false;
		}
		if (isReloading(stack)) {
			return false;
		}
		WeaponModifiers modifiers = WeaponModifiers.of(stack, def);
		int magazineSize = modifiers.magazineSize(stats.magazineSize());
		if (magazineSize > 0 && stack.getOrDefault(Firing.ammoComponent(stack), 0) <= 0) {
			WeaponAnimationEvents.trigger(player, stack, WeaponAnimationEvents.forSelection(stack, AnimationDefinition.Event.EMPTY));
			startReload(player, stack, def, stats, true);
			return false;
		}

		// Aiming with the aim key is a deliberate aim: ads_spread_multiplier applies. A quick shot with
		// use alone doesn't get it.
		float spreadDegrees = stats.accuracyDegrees() * modifiers.accuracyMultiplier()
				* (isAimKeyHeld(player) ? def.handling().adsSpreadMultiplier() : 1f);
		shoot(player, stack, hand, def, stats, modifiers, muzzlePosition(player, hand, def), player.getViewVector(1.0f),
				spreadDegrees, lockTarget, WeaponTargeting.raycastGroundPoint(world, player));
		NEXT_FIRE_TIME.put(player.getUUID(), now + Math.max(1, stats.cooldownTicks()));
		LAST_FIRED.put(player.getUUID(), now);
		float recoil = def.handling().recoilFor(player.isShiftKeyDown()) * modifiers.recoilMultiplier();
		if (recoil > 0f) {
			float yaw = (world.random.nextFloat() * 2f - 1f) * recoil * 0.25f;
			ServerPlayNetworking.send(player, new RecoilPayload(recoil, yaw));
		}
		if (magazineSize > 0 && stack.getOrDefault(Firing.ammoComponent(stack), 0) <= 0) {
			startReload(player, stack, def, stats, false);
		}
		return true;
	}

	/** One shot, by anyone (a player, a soldier): the projectiles (one per pellet), the muzzle
	 * effects, the fire animation, the sound, the fire delay recorded on the stack and one round
	 * taken from the magazine. direction: where it's aimed; groundTarget: the aim point for weapons
	 * guided to a point (AS missiles, rockets). The caller has checked it can fire. */
	public static void shoot(LivingEntity shooter, ItemStack stack, InteractionHand hand, HandheldDefinition def, WeaponStats stats,
			WeaponModifiers modifiers, Vec3 muzzle, Vec3 direction, float spreadDegrees, Entity lockTarget, Vec3 groundTarget) {
		ServerLevel world = (ServerLevel) shooter.level();
		int mode = stack.getOrDefault(ModComponents.MODE, 0);
		for (int pellet = 0; pellet < def.handling().pellets(); pellet++) {
			spawnProjectile(world, shooter, def, stats, mode, muzzle, direction, spreadDegrees, lockTarget, groundTarget);
		}
		FiringEffects.onFire(shooter, hand, def, stats, modifiers, muzzle);
		stack.set(ModComponents.COOLDOWN_UNTIL, world.getGameTime() + Math.max(1, stats.cooldownTicks()));
		WeaponAnimationEvents.trigger(shooter, stack, WeaponAnimationEvents.forSelection(stack, AnimationDefinition.Event.FIRE));
		if (modifiers.magazineSize(stats.magazineSize()) > 0) {
			stack.set(Firing.ammoComponent(stack), Math.max(0, stack.getOrDefault(Firing.ammoComponent(stack), 0) - 1));
		}
		playFireSound(shooter, stats, modifiers, muzzle);
	}

	/** One projectile (one pellet of a shotgun shot): position, spread, guidance, spawn. */
	private static void spawnProjectile(ServerLevel world, LivingEntity shooter, HandheldDefinition def, WeaponStats stats,
			int mode, Vec3 spawnPos, Vec3 direction, float spreadDegrees, Entity lockTarget, Vec3 groundTarget) {
		Item projectileItem = BuiltInRegistries.ITEM.getValue(def.projectileItem());
		VehicleProjectileEntity projectile = WeaponProjectileFactory.create(world, shooter, projectileItem.getDefaultInstance(), stats, mode);

		projectile.setPos(spawnPos.x, spawnPos.y, spawnPos.z);

		Vec3 velocity = direction.normalize().scale(stats.velocity());
		if (def.inheritShooterVelocity()) {
			velocity = velocity.add(shooter.getDeltaMovement());
		}
		velocity = WeaponTargeting.applyAccuracySpread(velocity, spreadDegrees, world.random);
		projectile.setDeltaMovement(velocity);
		ViewSpace.faceAlong(projectile, velocity);

		// Guidance - the same per-type setup AbstractVehicleEntity.tryFireWeapon() does.
		switch (stats.weaponType()) {
			case AS_MISSILE, MK_ROCKET -> projectile.tudursvehiclemod$setGuidanceTargetPos(groundTarget, stats.turnRateDegreesPerTick());
			case AA_MISSILE, AT_MISSILE, MISSILE -> {
				projectile.tudursvehiclemod$setMissileGuidanceTuning(stats.rigidityTimeTicks(), stats.proximityFuseDist());
				projectile.tudursvehiclemod$setTopAttack(stats.weaponType() == WeaponType.AT_MISSILE && stats.hasModes() && mode == 1);
				if (lockTarget != null) {
					projectile.tudursvehiclemod$setGuidanceTargetEntity(lockTarget.getId(), stats.turnRateDegreesPerTick());
				}
			}
			// Mode 0 is steered by the shooting player (camera follows the missile); mode 1 - and any
			// other shooter - homes on the ground point aimed at instead.
			case TV_MISSILE -> {
				if (shooter instanceof ServerPlayer player && !(stats.hasModes() && mode == 1)) {
					projectile.tudursvehiclemod$setTvControlled(player);
				} else {
					projectile.tudursvehiclemod$setGuidanceTargetPos(groundTarget, stats.turnRateDegreesPerTick());
				}
			}
			default -> {
			}
		}

		world.addFreshEntity(projectile);
		projectile.tudursvehiclemod$forceLoadSpawnChunk();
	}

	/** Eye position plus the definition's muzzle_offset (right, up, forward in the player's own view),
	 * mirrored for a left-side hand. Pulled back to the eye if a block is in the way, so a weapon
	 * held against a wall can't shoot through it. */
	private static Vec3 muzzlePosition(Player player, InteractionHand hand, HandheldDefinition def) {
		return ViewSpace.clearOfBlocks(player, ViewSpace.point(player, def.muzzleOffset(), ViewSpace.rightSide(player, hand)));
	}

	/** Same sound path as a vehicle weapon (WeaponFireSoundPayload, played by Tudur's Vehicle Mod's
	 * client), sent to everyone tracking the shooter plus the shooter. Attachments can replace the
	 * sound (a silencer) and scale its volume - which also scales how far it carries - and pitch. */
	private static void playFireSound(Entity shooter, WeaponStats stats, WeaponModifiers modifiers, Vec3 pos) {
		Optional<String> sound = modifiers.soundOverride().isPresent() ? modifiers.soundOverride() : stats.sound();
		if (sound.isEmpty()) {
			return;
		}
		playSound(shooter, sound.get(), pos, stats.soundVolume() * modifiers.volumeMultiplier(),
				stats.soundPitch() * modifiers.pitchMultiplier(), stats.soundPitchRandom());
	}

	/** Plays a named sound (any .ogg Tudur's Vehicle Mod's sound loader knows) at pos for everyone
	 * tracking the source, plus the source itself if it's a player. */
	public static void playSound(Entity source, String sound, Vec3 pos, float volume, float pitch, float pitchRandom) {
		WeaponFireSoundPayload payload = new WeaponFireSoundPayload(sound, pos.x, pos.y, pos.z, volume, pitch, pitchRandom);
		for (ServerPlayer tracking : PlayerLookup.tracking(source)) {
			ServerPlayNetworking.send(tracking, payload);
		}
		if (source instanceof ServerPlayer player) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** Plays a named sound at pos for every player within earshot (scaled by volume, like the vehicle
	 * mod's own weapon sounds) - for sounds with no player behind them (a mine, a grenade's effect). */
	public static void playSoundAt(ServerLevel world, String sound, Vec3 pos, float volume, float pitch, float pitchRandom) {
		WeaponFireSoundPayload payload = new WeaponFireSoundPayload(sound, pos.x, pos.y, pos.z, volume, pitch, pitchRandom);
		for (ServerPlayer listener : PlayerLookup.around(world, pos, Math.max(16.0, 16.0 * volume))) {
			ServerPlayNetworking.send(listener, payload);
		}
	}

	// ---------------------------------------------------------------- reloading

	public static boolean isReloading(ItemStack stack) {
		return stack.has(ModComponents.RELOAD_UNTIL);
	}

	/** Starts a reload unless one is running, the magazine is full, or there's nothing to load.
	 * notifyIfEmpty shows "no ammo" when the player has none. */
	public static void startReload(ServerPlayer player, ItemStack stack, HandheldDefinition def, WeaponStats stats,
			boolean notifyIfEmpty) {
		WeaponModifiers modifiers = WeaponModifiers.of(stack, def);
		int magazineSize = modifiers.magazineSize(stats.magazineSize());
		if (magazineSize <= 0 || isReloading(stack) || stack.getOrDefault(Firing.ammoComponent(stack), 0) >= magazineSize) {
			return;
		}
		if (availableRounds(player, def) <= 0) {
			if (notifyIfEmpty) {
				player.displayClientMessage(Component.translatable("message.tudursguns.no_ammo"), true);
			}
			return;
		}
		long now = player.level().getGameTime();
		int reloadTicks = modifiers.reloadTicks(stats.reloadTicks());
		FiringEffects.onReload(player, stack, def, stats, magazineSize - stack.getOrDefault(Firing.ammoComponent(stack), 0));
		stack.set(ModComponents.RELOAD_UNTIL, now + reloadTicks);
		WeaponAnimationEvents.trigger(player, stack, WeaponAnimationEvents.forSelection(stack, AnimationDefinition.Event.RELOAD), reloadTicks);
		def.reloadSound().ifPresent(sound -> playSound(player, sound, player.getEyePosition(), 1.0f, 1.0f, 0.05f));
	}

	/** Finishes a reload whose time is up: loads as many rounds as the inventory can supply. */
	public static void tickReload(Player player, ItemStack stack, HandheldDefinition def, WeaponStats stats) {
		Long until = stack.get(ModComponents.RELOAD_UNTIL);
		if (until == null || player.level().getGameTime() < until) {
			return;
		}
		stack.remove(ModComponents.RELOAD_UNTIL);
		int loaded = stack.getOrDefault(Firing.ammoComponent(stack), 0);
		int wanted = Math.max(0, WeaponModifiers.of(stack, def).magazineSize(stats.magazineSize()) - loaded);
		stack.set(Firing.ammoComponent(stack), loaded + takeRounds(player, def, wanted));
		WeaponAnimationEvents.trigger(player, stack, WeaponAnimationEvents.forSelection(stack, AnimationDefinition.Event.RELOAD_END));
	}

	/** Rounds the player could load right now. Unlimited without ammo, or in creative mode. */
	public static int availableRounds(Player player, HandheldDefinition def) {
		AmmoSupply supply = AmmoSupply.of(def);
		if (supply == null || player.isCreative()) {
			return Integer.MAX_VALUE;
		}
		long rounds = (long) supply.countItems(player) * supply.roundsPerItem();
		return (int) Math.min(Integer.MAX_VALUE, rounds);
	}

	/** Consumes ammo items for up to wanted rounds; returns the rounds actually loaded. One ammo item
	 * gives rounds_per_ammo_item rounds, so a partly used item's remainder is lost - like
	 * discarding a partly used magazine. */
	private static int takeRounds(Player player, HandheldDefinition def, int wanted) {
		if (wanted <= 0) {
			return 0;
		}
		AmmoSupply supply = AmmoSupply.of(def);
		if (supply == null || player.isCreative()) {
			return wanted;
		}
		int perItem = supply.roundsPerItem();
		int itemsWanted = (wanted + perItem - 1) / perItem;
		int itemsTaken = 0;
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize() && itemsTaken < itemsWanted; slot++) {
			ItemStack candidate = inventory.getItem(slot);
			if (supply.matches().test(candidate)) {
				int take = Math.min(candidate.getCount(), itemsWanted - itemsTaken);
				candidate.shrink(take);
				itemsTaken += take;
			}
		}
		return Math.min(wanted, itemsTaken * perItem);
	}

	// ---------------------------------------------------------------- attachments

	private static final Identifier MELEE_BONUS_MODIFIER_ID = Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, "attachment_melee_bonus");

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
		syncMeleeDamage(stack, def);
	}

	/** Sets the stack's attack damage modifier to the definition's melee_damage plus the attachments'
	 * bonuses (a bayonet), or removes it when that's 0. Unchanged stacks aren't touched, so it's cheap
	 * enough to call every inventory tick - which is how a weapon made by a recipe or command gets it. */
	public static void syncMeleeDamage(ItemStack stack, HandheldDefinition def) {
		float damage = def.handling().meleeDamage() + WeaponModifiers.of(stack, def).meleeDamageBonus();
		if (damage > 0f) {
			ItemAttributeModifiers wanted = ItemAttributeModifiers.builder()
					.add(Attributes.ATTACK_DAMAGE,
							new AttributeModifier(MELEE_BONUS_MODIFIER_ID, damage, AttributeModifier.Operation.ADD_VALUE),
							EquipmentSlotGroup.MAINHAND)
					.build();
			if (!wanted.equals(stack.get(DataComponents.ATTRIBUTE_MODIFIERS))) {
				stack.set(DataComponents.ATTRIBUTE_MODIFIERS, wanted);
			}
		} else if (stack.has(DataComponents.ATTRIBUTE_MODIFIERS)) {
			stack.remove(DataComponents.ATTRIBUTE_MODIFIERS);
		}
	}

	// ---------------------------------------------------------------- burst fire

	/** A burst in progress: the rest of its shots, fired at the weapon's own Delay from the same stack. */
	private record Burst(InteractionHand hand, ItemStack stack, int remaining) {
	}

	private static final Map<UUID, Burst> BURSTS = new ConcurrentHashMap<>();

	/** After a burst weapon's first shot: queue the rest (burst_count - 1). */
	public static void startBurst(ServerPlayer player, InteractionHand hand, HandheldDefinition def) {
		if (def.fireMode() == HandheldDefinition.FireMode.BURST && def.handling().burstCount() > 1) {
			BURSTS.put(player.getUUID(), new Burst(hand, player.getItemInHand(hand), def.handling().burstCount() - 1));
		}
	}

	/** Fires the next shot of a burst when the Delay allows. A burst ends early when the magazine runs
	 * dry or the weapon leaves the hand. */
	public static void tickBurst(ServerPlayer player, ItemStack stack, HandheldDefinition def, WeaponStats stats) {
		Burst burst = BURSTS.get(player.getUUID());
		if (burst == null) {
			return;
		}
		if (player.getItemInHand(burst.hand()) != burst.stack()) {
			BURSTS.remove(player.getUUID());
			return;
		}
		if (burst.stack() != stack) {
			return;
		}
		if (player.level().getGameTime() < NEXT_FIRE_TIME.getOrDefault(player.getUUID(), Long.MIN_VALUE)) {
			return;
		}
		if (tryFire(player, stack, burst.hand(), def, stats, null) && burst.remaining() > 1) {
			BURSTS.put(player.getUUID(), new Burst(burst.hand(), stack, burst.remaining() - 1));
		} else {
			BURSTS.remove(player.getUUID());
		}
	}

	// ---------------------------------------------------------------- aiming

	/** Players holding the aim key (reported by their client). Holding use also aims, but that's
	 * already visible to everyone through the vanilla "using item" state. Not saved. */
	private static final Set<UUID> AIM_KEY_HELD = ConcurrentHashMap.newKeySet();

	/** Records the player's aim key state; returns true if it changed. */
	public static boolean setAimKeyHeld(ServerPlayer player, boolean held) {
		boolean changed = held ? AIM_KEY_HELD.add(player.getUUID()) : AIM_KEY_HELD.remove(player.getUUID());
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

	/** A shot asked for with use from the hip, waiting for the weapon (this stack) to be fully up. */
	private record PendingShot(InteractionHand hand, ItemStack stack, long requestedAt) {
	}

	private static final Map<UUID, PendingShot> PENDING_SHOTS = new ConcurrentHashMap<>();

	/** A pending shot not fired within this long after it was asked for is dropped. */
	private static final int PENDING_SHOT_TIMEOUT_TICKS = 40;

	private static void startRaising(ServerPlayer player) {
		RAISE_START.putIfAbsent(player.getUUID(), player.level().getGameTime());
	}

	/** Use pressed: raising starts now - unless the weapon is already up (aim key) or on its way up
	 * for a shot already waiting. */
	public static void startRaisingForUse(ServerPlayer player) {
		if (isAimKeyHeld(player) || PENDING_SHOTS.containsKey(player.getUUID())) {
			startRaising(player);
		} else {
			RAISE_START.put(player.getUUID(), player.level().getGameTime());
		}
	}

	/** The weapon comes down unless the aim key still holds it up, or a shot is still waiting to go. */
	public static void stopRaisingUnlessAiming(ServerPlayer player) {
		if (!isAimKeyHeld(player) && !PENDING_SHOTS.containsKey(player.getUUID())) {
			RAISE_START.remove(player.getUUID());
		}
	}

	/** Whether the weapon is fully up: raising began at least raise_ticks ago. */
	public static boolean isRaised(ServerPlayer player, HandheldDefinition def) {
		int raiseTicks = def.raiseTicks();
		if (raiseTicks <= 0) {
			return true;
		}
		Long start = RAISE_START.get(player.getUUID());
		return start != null && player.level().getGameTime() - start >= raiseTicks;
	}

	/** Whether the weapon is in its fire delay (as recorded on the stack - the client can see it too). */
	public static boolean isCoolingDown(ItemStack stack, long now) {
		Long until = stack.get(ModComponents.COOLDOWN_UNTIL);
		return until != null && now < until;
	}

	public static void requestShot(ServerPlayer player, InteractionHand hand) {
		PENDING_SHOTS.put(player.getUUID(), new PendingShot(hand, player.getItemInHand(hand), player.level().getGameTime()));
	}

	/** Fires a waiting shot once the weapon is up (even if use was already released - a quick click
	 * from the hip still fires, after the raise). Called every tick for the weapon in the player's
	 * inventory. */
	public static void tickPendingShot(ServerPlayer player, ItemStack stack, HandheldDefinition base, Firing firing) {
		PendingShot pending = PENDING_SHOTS.get(player.getUUID());
		if (pending == null || pending.stack() != stack) {
			return;
		}
		if (isRaised(player, base)) {
			PENDING_SHOTS.remove(player.getUUID());
			if (tryFire(player, stack, pending.hand(), firing.definition(), firing.stats(), null)) {
				startBurst(player, pending.hand(), firing.definition());
			}
			if (!(player.isUsingItem() && player.getUseItem() == stack)) {
				stopRaisingUnlessAiming(player);
			}
		}
	}

	/** Housekeeping every tick: drops a waiting shot that timed out or whose weapon left the hand, and
	 * forgets the raise once nothing holds the weapon up (the item was switched while in use). */
	public static void tickRaiseState(ServerPlayer player) {
		PendingShot pending = PENDING_SHOTS.get(player.getUUID());
		if (pending != null && (player.level().getGameTime() - pending.requestedAt() > PENDING_SHOT_TIMEOUT_TICKS
				|| player.getVehicle() != null
				|| player.getItemInHand(pending.hand()) != pending.stack())) {
			PENDING_SHOTS.remove(player.getUUID());
		}
		boolean usingWeapon = player.isUsingItem() && player.getUseItem().getItem() instanceof HandheldWeaponItem;
		if (!usingWeapon) {
			stopRaisingUnlessAiming(player);
		}
	}

	public static boolean isAimKeyHeld(ServerPlayer player) {
		return AIM_KEY_HELD.contains(player.getUUID());
	}

	/** True while the player is fighting with a weapon: holding use (firing, locking, aiming) or the
	 * aim key, or within ACTION_COOLDOWN_TICKS of their last shot. Resupply (the ammo box) waits until
	 * the weapon has been lowered, so it can't turn into an endless magazine in the middle of a fight. */
	public static boolean isInAction(ServerPlayer player) {
		if (player.isUsingItem() || isAimKeyHeld(player)) {
			return true;
		}
		Long last = LAST_FIRED.get(player.getUUID());
		return last != null && player.level().getGameTime() - last < ACTION_COOLDOWN_TICKS;
	}

	// ---------------------------------------------------------------- modes

	/** Switches between the weapon's own fire and its underbarrel launcher. Returns false if no
	 * launcher is fitted. A reload in progress is cancelled (it was loading the other one). */
	public static boolean toggleUnderbarrel(ServerPlayer player, ItemStack stack, HandheldDefinition def) {
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
		WeaponAnimationEvents.trigger(player, stack, AnimationDefinition.Event.UNDERBARREL_SWITCH);
		player.displayClientMessage(Component.translatable(selected ? "message.tudursguns.underbarrel.on" : "message.tudursguns.underbarrel.off"), true);
		return true;
	}

	/** Cycles the ModeNum mode (MachineGun HE rounds, Rocket bomblets, ATMissile top attack, TVMissile
	 * guided mode). Returns false if the weapon has only one mode. */
	public static boolean cycleMode(ServerPlayer player, ItemStack stack, WeaponStats stats) {
		int modes = Math.max(1, stats.modeNum());
		if (modes < 2) {
			return false;
		}
		int next = (stack.getOrDefault(ModComponents.MODE, 0) + 1) % modes;
		stack.set(ModComponents.MODE, next);
		WeaponAnimationEvents.trigger(player, stack, AnimationDefinition.Event.MODE);
		player.displayClientMessage(Component.translatable("message.tudursguns.mode", next + 1, modes), true);
		return true;
	}

	// ---------------------------------------------------------------- lock-on

	/** Called every tick while the use key is held with an AA/AT/Missile weapon: keeps the target
	 * under the crosshair and counts up while it stays the same, exactly like a vehicle's lock
	 * (LockTime plus LockTimePerBlock x distance; any change of target restarts the count). */
	public static void updateLock(ServerPlayer player, WeaponStats stats) {
		ServerLevel world = (ServerLevel) player.level();
		Entity target = WeaponTargeting.findLockOnTarget(world, player, stats.weaponType(), stats.lockRange(), null);
		LockState previous = LOCKS.get(player.getUUID());
		LockState next;
		if (target == null) {
			next = null;
		} else {
			double distance = target.position().distanceTo(player.getEyePosition());
			int required = (int) Math.round(stats.lockTimeTicks() + stats.lockTimePerBlock() * distance);
			int progress = previous != null && previous.targetId() == target.getId() ? previous.progressTicks() + 1 : 0;
			next = new LockState(target.getId(), progress, required);
		}
		setLock(player, next);
	}

	/** The completed lock's target, or null if the lock isn't complete (or the target is gone). */
	public static Entity completedLockTarget(ServerPlayer player) {
		LockState state = LOCKS.get(player.getUUID());
		if (state == null || state.progressTicks() < state.requiredTicks()) {
			return null;
		}
		return player.level().getEntity(state.targetId());
	}

	public static void clearLock(ServerPlayer player) {
		setLock(player, null);
	}

	private static void setLock(ServerPlayer player, LockState next) {
		LockState previous = next == null ? LOCKS.remove(player.getUUID()) : LOCKS.put(player.getUUID(), next);
		if (Objects.equals(previous, next)) {
			return;
		}
		ServerPlayNetworking.send(player, next == null ? LockStatePayload.NONE
				: new LockStatePayload(next.targetId(), next.progressTicks(), next.requiredTicks()));
	}

	public static boolean hasLockState(ServerPlayer player) {
		return LOCKS.containsKey(player.getUUID());
	}
}
