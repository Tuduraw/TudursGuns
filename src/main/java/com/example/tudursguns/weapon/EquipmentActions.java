package com.example.tudursguns.weapon;

import com.example.tudursguns.entity.LaserSpotEntity;
import com.example.tudursguns.entity.MarkerEntity;
import com.example.tudursguns.entity.MineEntity;
import com.example.tudursguns.handheld.EquipmentDefinition;
import com.example.tudursguns.handheld.MineDefinition;
import com.example.tudursguns.mixin.AbstractVehicleEntityAccessor;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Server side of the support equipment (see EquipmentDefinition for what each type does). */
public final class EquipmentActions {

	private EquipmentActions() {
	}

	/** Same "stationary" threshold as the ammo box and Tudur's Vehicle Mod's own supply. */
	private static final double STATIONARY_VELOCITY_SQUARED = 0.02 * 0.02;

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
	private static void useUp(ServerPlayerEntity player, ItemStack stack, Hand hand, EquipmentDefinition def) {
		if (player.isCreative()) {
			return;
		}
		if (def.uses() > 0) {
			stack.damage(1, player, hand);
		} else {
			stack.decrement(1);
		}
	}

	/** Durability-only wear: nothing for equipment without uses (a detonator isn't used up). */
	private static void wear(ServerPlayerEntity player, ItemStack stack, Hand hand, EquipmentDefinition def) {
		if (def.uses() > 0 && !player.isCreative()) {
			stack.damage(1, player, hand);
		}
	}

	/** The nearest entity of the given class the player is looking at within range, not behind a block. */
	public static <T extends Entity> T lookedAt(ServerPlayerEntity player, Class<T> type, double range, Predicate<? super T> filter) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVec(1.0f).multiply(range));
		BlockHitResult blockHit = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		double limit = blockHit.getType() == HitResult.Type.MISS ? range : blockHit.getPos().distanceTo(eye);
		T best = null;
		double bestDistance = limit;
		for (T candidate : world.getEntitiesByClass(type, new Box(eye, end).expand(1.0),
				candidate -> candidate != player && !candidate.isRemoved() && filter.test(candidate))) {
			Box box = candidate.getBoundingBox().expand(0.15);
			Optional<Vec3d> hit = box.contains(eye) ? Optional.of(eye) : box.raycast(eye, end);
			if (hit.isPresent() && hit.get().distanceTo(eye) < bestDistance) {
				best = candidate;
				bestDistance = hit.get().distanceTo(eye);
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- first aid

	/** Heals the creature looked at within range, else the player. */
	public static void firstAid(ServerPlayerEntity player, ItemStack stack, Hand hand, EquipmentDefinition def) {
		LivingEntity target = lookedAt(player, LivingEntity.class, def.effectiveRange(),
				living -> living.isAlive() && !(living instanceof MarkerEntity));
		if (target == null) {
			target = player;
		}
		target.heal(def.heal());
		if (def.regenerationTicks() > 0) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, def.regenerationTicks(), 0));
		}
		playUseSound(player, def);
		useUp(player, stack, hand, def);
	}

	// ---------------------------------------------------------------- repair kit

	/** Called every tick while held; every use_ticks it repairs the stationary vehicle looked at.
	 * Returns false to stop using (no vehicle, or fully repaired). */
	public static boolean repairTick(ServerPlayerEntity player, ItemStack stack, Hand hand, EquipmentDefinition def, int ticksUsed) {
		AbstractVehicleEntity vehicle = lookedAt(player, AbstractVehicleEntity.class, def.effectiveRange(),
				candidate -> candidate.isAlive() && !candidate.tudursvehiclemod$isDestroyed()
						&& candidate.getVelocity().horizontalLengthSquared() <= STATIONARY_VELOCITY_SQUARED);
		if (vehicle == null) {
			player.sendMessage(Text.translatable("message.tudursguns.repair.no_vehicle"), true);
			return false;
		}
		if (vehicle.getHealth() >= vehicle.getMaxHealth()) {
			player.sendMessage(Text.translatable("message.tudursguns.repair.full"), true);
			return false;
		}
		if (ticksUsed > 0 && ticksUsed % def.useTicks() == 0) {
			for (int i = 0; i < def.repairSteps(); i++) {
				((AbstractVehicleEntityAccessor) vehicle).tudursguns$receiveHealthSupply();
			}
			playUseSound(player, def);
			wear(player, stack, hand, def);
			player.sendMessage(Text.translatable("message.tudursguns.repair.progress",
					Math.round(100f * vehicle.getHealth() / Math.max(1f, vehicle.getMaxHealth()))), true);
		}
		return true;
	}

	// ---------------------------------------------------------------- defuse kit

	/** Called every tick while held. Returns false to stop using (no mine, done, or changed target). */
	public static boolean defuseTick(ServerPlayerEntity player, ItemStack stack, Hand hand, EquipmentDefinition def) {
		MineEntity mine = lookedAt(player, MineEntity.class, def.effectiveRange(), candidate -> true);
		DefuseProgress progress = DEFUSING.get(player.getUuid());
		if (mine == null) {
			DEFUSING.remove(player.getUuid());
			return false;
		}
		if (progress == null || !progress.mine().equals(mine.getUuid())) {
			progress = new DefuseProgress(mine.getUuid(), 0);
		}
		progress = new DefuseProgress(progress.mine(), progress.ticks() + 1);
		MineDefinition mineDef = mine.definition();
		int needed = mineDef == null ? 1 : Math.max(1, mineDef.defuseTicks());
		if (progress.ticks() >= needed) {
			DEFUSING.remove(player.getUuid());
			mine.pickUp(player);
			playUseSound(player, def);
			wear(player, stack, hand, def);
			player.sendMessage(Text.translatable("message.tudursguns.defuse.done"), true);
			return false;
		}
		DEFUSING.put(player.getUuid(), progress);
		if (progress.ticks() % 10 == 0) {
			player.sendMessage(Text.translatable("message.tudursguns.defuse.progress", Math.round(100f * progress.ticks() / needed)), true);
		}
		return true;
	}

	public static void stopDefusing(ServerPlayerEntity player) {
		DEFUSING.remove(player.getUuid());
	}

	// ---------------------------------------------------------------- detonator

	/** Sets off every armed remote charge the player placed within range. */
	public static void detonate(ServerPlayerEntity player, ItemStack stack, Hand hand, EquipmentDefinition def) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		double range = def.effectiveRange();
		var charges = world.getEntitiesByClass(MineEntity.class, player.getBoundingBox().expand(range),
				mine -> mine.isOwnedBy(player) && mine.isArmed() && mine.squaredDistanceTo(player) <= range * range
						&& mine.definition() != null && mine.definition().trigger() == MineDefinition.Trigger.REMOTE);
		playUseSound(player, def);
		for (MineEntity mine : charges) {
			mine.detonate(world);
		}
		player.sendMessage(Text.translatable("message.tudursguns.detonator.fired", charges.size()), true);
		if (!charges.isEmpty()) {
			wear(player, stack, hand, def);
		}
	}

	// ---------------------------------------------------------------- laser designator

	/** Called every tick while held: puts the spot where the player points. */
	public static void laserTick(ServerPlayerEntity player, EquipmentDefinition def) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0f);
		double range = def.effectiveRange();
		BlockHitResult blockHit = world.raycast(new RaycastContext(eye, eye.add(look.multiply(range)),
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, player));
		double distance = blockHit.getType() == HitResult.Type.MISS ? range : blockHit.getPos().distanceTo(eye);
		Entity entity = lookedAt(player, Entity.class, distance, candidate -> candidate.canHit() && !(candidate instanceof MarkerEntity));
		if (entity != null) {
			distance = Math.max(0.5, entity.getBoundingBox().raycast(eye, eye.add(look.multiply(range)))
					.map(hit -> hit.distanceTo(eye)).orElse(distance));
		}
		if (blockHit.getType() == HitResult.Type.MISS && entity == null) {
			clearLaser(player);
			return;
		}
		// Just in front of the surface, so the spot isn't inside a block.
		Vec3d pos = eye.add(look.multiply(Math.max(0.5, distance - 0.2)));
		LaserSpotEntity spot = LASER_SPOTS.get(player.getUuid());
		if (spot == null || spot.isRemoved() || spot.getEntityWorld() != world) {
			spot = LaserSpotEntity.create(world, pos);
			LASER_SPOTS.put(player.getUuid(), spot);
		}
		spot.refresh(world, pos);
	}

	public static void clearLaser(ServerPlayerEntity player) {
		LaserSpotEntity spot = LASER_SPOTS.remove(player.getUuid());
		if (spot != null) {
			spot.discard();
		}
	}

	private static void playUseSound(ServerPlayerEntity player, EquipmentDefinition def) {
		def.sound().ifPresent(sound -> HandheldCombat.playSound(player, sound, player.getEyePos(), 0.8f, 1.0f, 0.05f));
	}
}
