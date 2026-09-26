package com.example.tudursguns.entity;

import com.example.tudursguns.registry.ModEntityTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** A smoke grenade's cloud (or a signal grenade's column) - invisible itself, it keeps emitting smoke
 * particles for its duration. Clients darken the view while the camera is inside one (SmokeOverlay).
 *
 * Lock-on decoy: a smoke cloud (not a signal) draws missile locks onto itself. Tudur's Vehicle Mod's
 * lock-on picks whatever is closest to the centre of the shooter's view, so for every player near
 * enough to be aiming at it, the cloud keeps one invisible SmokeDecoyEntity exactly on that player's
 * line of sight where it passes through the cloud - dead centre, which beats any target in or behind
 * the smoke. A lock (vehicle or handheld alike) therefore settles on the smoke, and a missile fired
 * at it flies into the smoke. No change to Tudur's Vehicle Mod is needed.
 *
 * Not saved (the entity type has saving disabled) - a cloud is gone after a reload. */
public class SmokeCloudEntity extends Entity {

	private static final TrackedData<Float> RADIUS = DataTracker.registerData(SmokeCloudEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Integer> COLOR = DataTracker.registerData(SmokeCloudEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> DURATION = DataTracker.registerData(SmokeCloudEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> SIGNAL = DataTracker.registerData(SmokeCloudEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	/** Ticks for the cloud to billow out to its full radius. */
	private static final int GROW_TICKS = 40;
	/** Players further than this can't be locking onto anything near the cloud in practice. */
	private static final double DECOY_PLAYER_RANGE = 256.0;

	private final Map<UUID, SmokeDecoyEntity> decoys = new HashMap<>();

	public SmokeCloudEntity(EntityType<?> type, World world) {
		super(type, world);
		this.setNoGravity(true);
	}

	public static SmokeCloudEntity create(ServerWorld world, Vec3d pos, float radius, int durationTicks, int rgb, boolean signal) {
		SmokeCloudEntity cloud = new SmokeCloudEntity(ModEntityTypes.SMOKE_CLOUD, world);
		cloud.setPosition(pos.x, pos.y, pos.z);
		cloud.dataTracker.set(RADIUS, radius);
		cloud.dataTracker.set(DURATION, durationTicks);
		cloud.dataTracker.set(COLOR, rgb);
		cloud.dataTracker.set(SIGNAL, signal);
		world.spawnEntity(cloud);
		return cloud;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(RADIUS, 4f);
		builder.add(COLOR, 0x999999);
		builder.add(DURATION, 0);
		builder.add(SIGNAL, false);
	}

	public boolean isSignal() {
		return this.dataTracker.get(SIGNAL);
	}

	/** Current radius: grows over the first GROW_TICKS, thins out over the last quarter of the duration. */
	public float currentRadius() {
		float full = this.dataTracker.get(RADIUS);
		int duration = Math.max(1, this.dataTracker.get(DURATION));
		float grow = Math.min(1f, this.age / (float) GROW_TICKS);
		float remaining = (duration - this.age) / (float) duration;
		float fade = remaining < 0.25f ? Math.max(0f, remaining / 0.25f) : 1f;
		return full * grow * (0.5f + 0.5f * fade);
	}

	/** 0-1: how thick the smoke is right now (for the in-cloud view overlay). */
	public float density() {
		int duration = Math.max(1, this.dataTracker.get(DURATION));
		float remaining = (duration - this.age) / (float) duration;
		return Math.max(0f, Math.min(1f, remaining / 0.25f));
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.getEntityWorld() instanceof ServerWorld world)) {
			return;
		}
		if (this.age >= this.dataTracker.get(DURATION)) {
			this.discard();
			return;
		}
		if (this.age % 2 == 0) {
			emitParticles(world);
		}
		if (!isSignal()) {
			updateDecoys(world);
		}
	}

	private void emitParticles(ServerWorld world) {
		float radius = currentRadius();
		DustParticleEffect dust = new DustParticleEffect(this.dataTracker.get(COLOR), 4.0f);
		if (isSignal()) {
			// A tall, thin coloured column.
			world.spawnParticles(dust, true, true, this.getX(), this.getY() + 1.5, this.getZ(), 6, 0.3, 1.5, 0.3, 0.02);
			world.spawnParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, this.getX(), this.getY() + 0.5, this.getZ(), 1, 0.1, 0.1, 0.1, 0.01);
			return;
		}
		int count = Math.max(4, Math.round(radius * radius * 2f));
		world.spawnParticles(dust, true, true, this.getX(), this.getY() + radius * 0.4, this.getZ(),
				count, radius * 0.5, radius * 0.35, radius * 0.5, 0.01);
		world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, true, this.getX(), this.getY() + radius * 0.3, this.getZ(),
				Math.max(1, count / 6), radius * 0.45, radius * 0.25, radius * 0.45, 0.005);
	}

	/** Keeps one decoy per nearby player on that player's line of sight inside the cloud (or at the
	 * cloud's centre when the line misses it). */
	private void updateDecoys(ServerWorld world) {
		float radius = currentRadius();
		Vec3d center = this.getEntityPos().add(0, radius * 0.4, 0);
		java.util.Set<UUID> seen = new java.util.HashSet<>();
		for (ServerPlayerEntity player : world.getPlayers(candidate -> true)) {
			if (player.isSpectator() || player.squaredDistanceTo(center) > DECOY_PLAYER_RANGE * DECOY_PLAYER_RANGE) {
				continue;
			}
			seen.add(player.getUuid());
			Vec3d decoyPos = pointOnLineOfSight(player, center, radius);
			SmokeDecoyEntity decoy = this.decoys.get(player.getUuid());
			if (decoy == null || decoy.isRemoved()) {
				decoy = SmokeDecoyEntity.create(world, this, decoyPos);
				this.decoys.put(player.getUuid(), decoy);
			} else {
				decoy.setPosition(decoyPos.x, decoyPos.y, decoyPos.z);
			}
		}
		for (Iterator<Map.Entry<UUID, SmokeDecoyEntity>> it = this.decoys.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, SmokeDecoyEntity> entry = it.next();
			if (!seen.contains(entry.getKey())) {
				entry.getValue().discard();
				it.remove();
			}
		}
	}

	/** Closest point to the cloud's centre on the player's view ray, if that point is inside the cloud
	 * and in front of the player; otherwise the centre itself. */
	private static Vec3d pointOnLineOfSight(PlayerEntity player, Vec3d center, float radius) {
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0f);
		double along = center.subtract(eye).dotProduct(look);
		if (along > 2.0) {
			Vec3d closest = eye.add(look.multiply(along));
			if (closest.squaredDistanceTo(center) <= (double) radius * radius) {
				return closest;
			}
		}
		return center;
	}

	@Override
	public void remove(RemovalReason reason) {
		for (SmokeDecoyEntity decoy : this.decoys.values()) {
			decoy.discard();
		}
		this.decoys.clear();
		super.remove(reason);
	}

	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		return false;
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
	protected void readCustomData(ReadView view) {
	}

	@Override
	protected void writeCustomData(WriteView view) {
	}
}
