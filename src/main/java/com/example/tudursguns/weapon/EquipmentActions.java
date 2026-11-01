package com.example.tudursguns.weapon;

import com.example.tudursguns.entity.LaserSpotEntity;
import com.example.tudursguns.entity.MarkerEntity;
import com.example.tudursguns.entity.MineEntity;
import com.example.tudursguns.handheld.EquipmentDefinition;
import com.example.tudursguns.handheld.MineDefinition;
import com.example.tudursguns.mixin.AbstractVehicleEntityAccessor;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server side of the support equipment (see EquipmentDefinition for what each type does). */
public final class EquipmentActions {

	private EquipmentActions() {
	}

	/** Tudur's Vehicle Mod's own "stationary" threshold for supply (horizontal speed). */
	private static final double STATIONARY_VELOCITY_SQUARED = 0.02 * 0.02;

	/** A vehicle that can be resupplied or repaired: intact and standing still, as Tudur's Vehicle
	 * Mod's own supply vehicles require. */
	public static boolean isSuppliable(AbstractVehicleEntity vehicle) {
		return vehicle.isAlive() && !vehicle.tudursvehiclemod$isDestroyed()
				&& vehicle.getDeltaMovement().horizontalDistanceSqr() <= STATIONARY_VELOCITY_SQUARED;
	}

	private record DefuseProgress(UUID mine, int ticks) {
	}

	private static final Map<UUID, DefuseProgress> DEFUSING = new ConcurrentHashMap<>();
	private static final Map<UUID, LaserSpotEntity> LASER_SPOTS = new ConcurrentHashMap<>();

	public static void forget(UUID playerId) {
		DEFUSING.remove(playerId);
		LaserSpotEntity spot = LASER_SPOTS.remove(playerId);
		if (spot != null) {
			spot.discard();
		}
	}

	/** Uses up one use: durability if it has any, else one item from the stack. */
	private static void useUp(ServerPlayer player, ItemStack stack, InteractionHand hand, EquipmentDefinition def) {
		if (player.isCreative()) {
			return;
		}
		if (def.uses() > 0) {
			stack.hurtAndBreak(1, player, hand);
		} else {
			stack.shrink(1);
		}
	}

	/** Durability-only wear: nothing for equipment without uses (a detonator isn't used up). */
	private static void wear(ServerPlayer player, ItemStack stack, InteractionHand hand, EquipmentDefinition def) {
		if (def.uses() > 0 && !player.isCreative()) {
			stack.hurtAndBreak(1, player, hand);
		}
	}

	/** The nearest entity of the given class the player is looking at within range, not behind a block. */
	private static <T extends Entity> T lookedAt(ServerPlayer player, Class<T> type, double range, Predicate<? super T> filter) {
		ServerLevel world = (ServerLevel) player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0f).scale(range));
		BlockHitResult blockHit = world.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, player));
		double limit = blockHit.getType() == HitResult.Type.MISS ? range : blockHit.getLocation().distanceTo(eye);
		T best = null;
		double bestDistance = limit;
		for (T candidate : world.getEntitiesOfClass(type, new AABB(eye, end).inflate(1.0),
				candidate -> candidate != player && !candidate.isRemoved() && filter.test(candidate))) {
			AABB box = candidate.getBoundingBox().inflate(0.15);
			Optional<Vec3> hit = box.contains(eye) ? Optional.of(eye) : box.clip(eye, end);
			if (hit.isPresent() && hit.get().distanceTo(eye) < bestDistance) {
				best = candidate;
				bestDistance = hit.get().distanceTo(eye);
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- first aid

	/** Heals the creature looked at within range, else the player. */
	public static void firstAid(ServerPlayer player, ItemStack stack, InteractionHand hand, EquipmentDefinition def) {
		LivingEntity target = lookedAt(player, LivingEntity.class, def.effectiveRange(),
				living -> living.isAlive() && !(living instanceof MarkerEntity));
		if (target == null) {
			target = player;
		}
		target.heal(def.heal());
		if (def.regenerationTicks() > 0) {
			target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, def.regenerationTicks(), 0));
		}
		playUseSound(player, def);
		useUp(player, stack, hand, def);
	}

	// ---------------------------------------------------------------- repair kit

	/** Called every tick while held; every use_ticks it repairs the stationary vehicle looked at.
	 * Returns false to stop using (no vehicle, or fully repaired). */
	public static boolean repairTick(ServerPlayer player, ItemStack stack, InteractionHand hand, EquipmentDefinition def, int ticksUsed) {
		AbstractVehicleEntity vehicle = lookedAt(player, AbstractVehicleEntity.class, def.effectiveRange(),
				EquipmentActions::isSuppliable);
		if (vehicle == null) {
			player.displayClientMessage(Component.translatable("message.tudursguns.repair.no_vehicle"), true);
			return false;
		}
		if (vehicle.getHealth() >= vehicle.getMaxHealth()) {
			player.displayClientMessage(Component.translatable("message.tudursguns.repair.full"), true);
			return false;
		}
		if (ticksUsed > 0 && ticksUsed % def.useTicks() == 0) {
			for (int i = 0; i < def.repairSteps(); i++) {
				((AbstractVehicleEntityAccessor) vehicle).tudursguns$receiveHealthSupply();
			}
			playUseSound(player, def);
			wear(player, stack, hand, def);
			player.displayClientMessage(Component.translatable("message.tudursguns.repair.progress",
					Math.round(100f * vehicle.getHealth() / Math.max(1f, vehicle.getMaxHealth()))), true);
		}
		return true;
	}

	// ---------------------------------------------------------------- defuse kit

	/** Called every tick while held. Returns false to stop using (no mine, done, or changed target). */
	public static boolean defuseTick(ServerPlayer player, ItemStack stack, InteractionHand hand, EquipmentDefinition def) {
		MineEntity mine = lookedAt(player, MineEntity.class, def.effectiveRange(), candidate -> true);
		DefuseProgress progress = DEFUSING.get(player.getUUID());
		if (mine == null) {
			DEFUSING.remove(player.getUUID());
			return false;
		}
		if (progress == null || !progress.mine().equals(mine.getUUID())) {
			progress = new DefuseProgress(mine.getUUID(), 0);
		}
		progress = new DefuseProgress(progress.mine(), progress.ticks() + 1);
		MineDefinition mineDef = mine.definition();
		int needed = mineDef == null ? 1 : Math.max(1, mineDef.defuseTicks());
		if (progress.ticks() >= needed) {
			DEFUSING.remove(player.getUUID());
			mine.pickUp(player);
			playUseSound(player, def);
			wear(player, stack, hand, def);
			player.displayClientMessage(Component.translatable("message.tudursguns.defuse.done"), true);
			return false;
		}
		DEFUSING.put(player.getUUID(), progress);
		if (progress.ticks() % 10 == 0) {
			player.displayClientMessage(Component.translatable("message.tudursguns.defuse.progress", Math.round(100f * progress.ticks() / needed)), true);
		}
		return true;
	}

	public static void stopDefusing(ServerPlayer player) {
		DEFUSING.remove(player.getUUID());
	}

	// ---------------------------------------------------------------- detonator

	/** Sets off every armed remote charge the player placed within range. */
	public static void detonate(ServerPlayer player, ItemStack stack, InteractionHand hand, EquipmentDefinition def) {
		ServerLevel world = (ServerLevel) player.level();
		double range = def.effectiveRange();
		var charges = world.getEntitiesOfClass(MineEntity.class, player.getBoundingBox().inflate(range),
				mine -> mine.isOwnedBy(player) && mine.isArmed() && mine.distanceToSqr(player) <= range * range
						&& mine.definition() != null && mine.definition().trigger() == MineDefinition.Trigger.REMOTE);
		playUseSound(player, def);
		for (MineEntity mine : charges) {
			mine.detonate(world);
		}
		player.displayClientMessage(Component.translatable("message.tudursguns.detonator.fired", charges.size()), true);
		if (!charges.isEmpty()) {
			wear(player, stack, hand, def);
		}
	}

	// ---------------------------------------------------------------- laser designator

	/** Called every tick while held: puts the spot where the player points. */
	public static void laserTick(ServerPlayer player, EquipmentDefinition def) {
		ServerLevel world = (ServerLevel) player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		double range = def.effectiveRange();
		BlockHitResult blockHit = world.clip(new ClipContext(eye, eye.add(look.scale(range)),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
		double distance = blockHit.getType() == HitResult.Type.MISS ? range : blockHit.getLocation().distanceTo(eye);
		Entity entity = lookedAt(player, Entity.class, distance, candidate -> candidate.isPickable() && !(candidate instanceof MarkerEntity));
		if (entity != null) {
			distance = Math.max(0.5, entity.getBoundingBox().clip(eye, eye.add(look.scale(range)))
					.map(hit -> hit.distanceTo(eye)).orElse(distance));
		}
		if (blockHit.getType() == HitResult.Type.MISS && entity == null) {
			clearLaser(player);
			return;
		}
		// Just in front of the surface, so the spot isn't inside a block.
		Vec3 pos = eye.add(look.scale(Math.max(0.5, distance - 0.2)));
		LaserSpotEntity spot = LASER_SPOTS.get(player.getUUID());
		if (spot == null || spot.isRemoved() || spot.level() != world) {
			spot = LaserSpotEntity.create(world, pos);
			LASER_SPOTS.put(player.getUUID(), spot);
		}
		spot.refresh(world, pos);
	}

	public static void clearLaser(ServerPlayer player) {
		LaserSpotEntity spot = LASER_SPOTS.remove(player.getUUID());
		if (spot != null) {
			spot.discard();
		}
	}

	private static void playUseSound(ServerPlayer player, EquipmentDefinition def) {
		def.sound().ifPresent(sound -> HandheldCombat.playSound(player, sound, player.getEyePosition(), 0.8f, 1.0f, 0.05f));
	}
}
