package com.example.tudursguns.entity;

import com.example.tudursguns.armor.ArmorEffects;
import com.example.tudursguns.registry.ModEntityTypes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A smoke, signal or gas grenade's cloud - invisible itself, it keeps emitting smoke particles for
 * its duration (clients grey out the view inside one - VisionOverlay). Not saved.
 *
 * Smoke and gas draw missile locks: Tudur's Vehicle Mod locks onto whatever is nearest the centre of
 * the shooter's view, so for each nearby player the cloud keeps an invisible SmokeDecoyEntity on
 * that player's line of sight through it. Gas also sickens anything breathing it without a gas mask. */
public class SmokeCloudEntity extends Entity {

	private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(SmokeCloudEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(SmokeCloudEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DURATION = SynchedEntityData.defineId(SmokeCloudEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(SmokeCloudEntity.class, EntityDataSerializers.INT);

	/** SMOKE: hides and draws locks. SIGNAL: a coloured marker column only. GAS: smoke that also sickens. */
	public enum Kind {
		SMOKE, SIGNAL, GAS
	}

	/** Ticks for the cloud to billow out to its full radius. */
	private static final int GROW_TICKS = 40;
	/** Players further than this can't be locking onto anything near the cloud in practice. */
	private static final double DECOY_PLAYER_RANGE = 256.0;

	private final Map<UUID, SmokeDecoyEntity> decoys = new HashMap<>();

	public SmokeCloudEntity(EntityType<?> type, Level world) {
		super(type, world);
		this.setNoGravity(true);
	}

	public static SmokeCloudEntity create(ServerLevel world, Vec3 pos, float radius, int durationTicks, int rgb, Kind kind) {
		SmokeCloudEntity cloud = new SmokeCloudEntity(ModEntityTypes.SMOKE_CLOUD, world);
		cloud.setPos(pos.x, pos.y, pos.z);
		cloud.entityData.set(RADIUS, radius);
		cloud.entityData.set(DURATION, durationTicks);
		cloud.entityData.set(COLOR, rgb);
		cloud.entityData.set(KIND, kind.ordinal());
		world.addFreshEntity(cloud);
		return cloud;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(RADIUS, 4f);
		builder.define(COLOR, 0x999999);
		builder.define(DURATION, 0);
		builder.define(KIND, Kind.SMOKE.ordinal());
	}

	public Kind kind() {
		Kind[] kinds = Kind.values();
		int index = this.entityData.get(KIND);
		return index >= 0 && index < kinds.length ? kinds[index] : Kind.SMOKE;
	}

	public boolean isSignal() {
		return kind() == Kind.SIGNAL;
	}

	/** Current radius: grows over the first GROW_TICKS, thins out over the last quarter of the duration. */
	public float currentRadius() {
		float full = this.entityData.get(RADIUS);
		int duration = Math.max(1, this.entityData.get(DURATION));
		float grow = Math.min(1f, this.tickCount / (float) GROW_TICKS);
		float remaining = (duration - this.tickCount) / (float) duration;
		float fade = remaining < 0.25f ? Math.max(0f, remaining / 0.25f) : 1f;
		return full * grow * (0.5f + 0.5f * fade);
	}

	/** 0-1: how thick the smoke is right now (for the in-cloud view overlay). */
	public float density() {
		int duration = Math.max(1, this.entityData.get(DURATION));
		float remaining = (duration - this.tickCount) / (float) duration;
		return Math.max(0f, Math.min(1f, remaining / 0.25f));
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel world)) {
			return;
		}
		if (this.tickCount >= this.entityData.get(DURATION)) {
			this.discard();
			return;
		}
		if (this.tickCount % 2 == 0) {
			emitParticles(world);
		}
		if (!isSignal()) {
			updateDecoys(world);
		}
		if (kind() == Kind.GAS && this.tickCount % 20 == 0) {
			gas(world);
		}
	}

	private void gas(ServerLevel world) {
		float radius = currentRadius();
		Vec3 center = this.position().add(0, radius * 0.4, 0);
		for (LivingEntity living : world.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
				living -> living.isAlive() && !(living instanceof MarkerEntity) && !(living instanceof ArmorStand)
						&& !(living instanceof Player player && (player.isCreative() || player.isSpectator()))
						&& living.getEyePosition().distanceToSqr(center) <= (double) radius * radius
						&& !ArmorEffects.hasGasProtection(living))) {
			living.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200, 0));
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
			living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
		}
	}

	private void emitParticles(ServerLevel world) {
		float radius = currentRadius();
		DustParticleOptions dust = new DustParticleOptions(this.entityData.get(COLOR), 4.0f);
		if (isSignal()) {
			// A tall, thin coloured column.
			world.sendParticles(dust, true, true, this.getX(), this.getY() + 1.5, this.getZ(), 6, 0.3, 1.5, 0.3, 0.02);
			world.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, this.getX(), this.getY() + 0.5, this.getZ(), 1, 0.1, 0.1, 0.1, 0.01);
			return;
		}
		int count = Math.max(4, Math.round(radius * radius * 2f));
		world.sendParticles(dust, true, true, this.getX(), this.getY() + radius * 0.4, this.getZ(),
				count, radius * 0.5, radius * 0.35, radius * 0.5, 0.01);
		world.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, true, this.getX(), this.getY() + radius * 0.3, this.getZ(),
				Math.max(1, count / 6), radius * 0.45, radius * 0.25, radius * 0.45, 0.005);
	}

	/** Keeps one decoy per nearby player on that player's line of sight inside the cloud (or at the
	 * cloud's centre when the line misses it). */
	private void updateDecoys(ServerLevel world) {
		float radius = currentRadius();
		Vec3 center = this.position().add(0, radius * 0.4, 0);
		Set<UUID> seen = new HashSet<>();
		for (ServerPlayer player : world.players()) {
			if (player.isSpectator() || player.distanceToSqr(center) > DECOY_PLAYER_RANGE * DECOY_PLAYER_RANGE) {
				continue;
			}
			seen.add(player.getUUID());
			Vec3 decoyPos = pointOnLineOfSight(player, center, radius);
			SmokeDecoyEntity decoy = this.decoys.get(player.getUUID());
			if (decoy == null || decoy.isRemoved()) {
				decoy = SmokeDecoyEntity.create(world, this, decoyPos);
				this.decoys.put(player.getUUID(), decoy);
			} else {
				decoy.setPos(decoyPos.x, decoyPos.y, decoyPos.z);
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
	private static Vec3 pointOnLineOfSight(Player player, Vec3 center, float radius) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		double along = center.subtract(eye).dot(look);
		if (along > 2.0) {
			Vec3 closest = eye.add(look.scale(along));
			if (closest.distanceToSqr(center) <= (double) radius * radius) {
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
	public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
		return false;
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
	protected void readAdditionalSaveData(ValueInput view) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput view) {
	}
}
