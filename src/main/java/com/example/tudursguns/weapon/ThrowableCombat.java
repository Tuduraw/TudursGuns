package com.example.tudursguns.weapon;

import com.example.tudursguns.entity.SmokeCloudEntity;
import com.example.tudursguns.handheld.ThrowableDefinition;
import com.example.tudursguns.network.FlashPayload;
import com.example.tudursvehiclemod.asset.WeaponStats;
import com.example.tudursvehiclemod.asset.WeaponStatsLoader;
import com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity;
import com.example.tudursvehiclemod.entity.projectile.WeaponProjectileFactory;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server side of throwing: pulling the pin (cooking), throwing, and the effects that aren't a plain
 * explosion.
 *
 * The thrown object is a Tudur's Vehicle Mod projectile set up from the throwable's weapon file, with
 * its time fuse set to whatever is left of the throwable's own fuse. A projectile carrying an effect
 * is remembered here, and when it disappears in the world (its fuse ran out, or it hit something)
 * the effect happens where it was - see onProjectileRemoved. */
public final class ThrowableCombat {

	private ThrowableCombat() {
	}

	/** DelayFuse used when the weapon file sets none: long enough that a bouncing throwable keeps
	 * rolling until its time fuse, rather than going off at the first impact. */
	private static final int NO_IMPACT_FUSE_TICKS = 72000;

	/** World time each player pulled the pin of the throwable they're holding. Not saved. */
	private static final Map<UUID, Long> PIN_PULLED_AT = new ConcurrentHashMap<>();
	/** Thrown projectiles that still owe an effect, by projectile UUID. */
	private static final Map<UUID, ThrowableDefinition.Effect> PENDING_EFFECTS = new ConcurrentHashMap<>();

	/** Forgets thrown projectiles' pending effects (the server is stopping). */
	public static void clear() {
		PIN_PULLED_AT.clear();
		PENDING_EFFECTS.clear();
	}

	public static void forget(UUID playerId) {
		PIN_PULLED_AT.remove(playerId);
	}

	public static void pullPin(ServerPlayer player, ThrowableDefinition def) {
		PIN_PULLED_AT.put(player.getUUID(), player.level().getGameTime());
		def.pinSound().ifPresent(sound -> HandheldCombat.playSound(player, sound, player.getEyePosition(), 0.6f, 1.0f, 0.05f));
	}

	/** Ticks the held throwable has been cooking (0 if not cookable or no pin pulled). */
	public static int cookedTicks(ServerPlayer player, ThrowableDefinition def) {
		Long pulled = PIN_PULLED_AT.get(player.getUUID());
		if (!def.cooks() || pulled == null) {
			return 0;
		}
		return (int) Math.max(0, player.level().getGameTime() - pulled);
	}

	/** Called every tick while held with the pin out: a cookable throwable held past its fuse goes off
	 * in the thrower's hand. Returns true if it did. */
	public static boolean checkCookedOff(ServerPlayer player, ItemStack stack, ThrowableDefinition def) {
		if (!def.cooks() || cookedTicks(player, def) < def.fuseTicks()) {
			return false;
		}
		launch(player, stack, def, Vec3.ZERO, player.getEyePosition().add(0, -0.3, 0), 1);
		PIN_PULLED_AT.remove(player.getUUID());
		return true;
	}

	/** Releases the throw: overhand, or underhand (a gentle lob) while the aim key is held. */
	public static void throwHeld(ServerPlayer player, ItemStack stack, ThrowableDefinition def) {
		boolean underhand = HandheldCombat.isAimKeyHeld(player);
		int cooked = cookedTicks(player, def);
		PIN_PULLED_AT.remove(player.getUUID());
		float speed = underhand ? def.underhandVelocity() : def.throwVelocity();
		Vec3 direction = underhand ? lobDirection(player) : player.getViewVector(1.0f);
		Vec3 velocity = direction.scale(speed).add(player.getDeltaMovement());
		int remainingFuse = def.fuseTicks() > 0 ? Math.max(1, def.fuseTicks() - cooked) : -1;
		launch(player, stack, def, velocity, throwOrigin(player), remainingFuse);
	}

	/** Underhand: the view direction tilted 20 degrees down (a short, low lob at the feet ahead). */
	private static Vec3 lobDirection(ServerPlayer player) {
		float pitch = Mth.clamp(player.getXRot() + 20f, -90f, 90f);
		return Vec3.directionFromRotation(pitch, player.getYRot());
	}

	/** Slightly in front of and below the eyes - pulled back to the eyes if a block is in the way. */
	private static Vec3 throwOrigin(ServerPlayer player) {
		return ViewSpace.clearOfBlocks(player, player.getEyePosition().add(player.getViewVector(1.0f).scale(0.4)).add(0, -0.1, 0));
	}

	/** Spawns the thrown projectile and takes one throwable from the stack (not in creative). */
	private static void launch(ServerPlayer player, ItemStack stack, ThrowableDefinition def, Vec3 velocity, Vec3 origin,
			int timeFuseTicks) {
		ServerLevel world = (ServerLevel) player.level();
		WeaponStats stats = WeaponStatsLoader.get(def.weapon());
		ItemStack shown = stack.copyWithCount(1);
		VehicleProjectileEntity projectile = WeaponProjectileFactory.create(world, player, shown, stats, 0);
		int delayFuse;
		if (def.impact()) {
			// Goes off at the first thing it hits (-1: no DelayFuse, the vehicle mod's own default), and
			// doesn't bounce off it first.
			delayFuse = stats.delayFuseTicks();
			projectile.tudursvehiclemod$setBounceStrength(0f);
		} else {
			delayFuse = stats.delayFuseTicks() >= 0 ? stats.delayFuseTicks() : NO_IMPACT_FUSE_TICKS;
		}
		int timeFuse = timeFuseTicks > 0 ? timeFuseTicks : stats.timeFuseTicks();
		projectile.tudursvehiclemod$setFuseTicks(delayFuse, timeFuse);
		projectile.setPos(origin.x, origin.y, origin.z);
		projectile.setDeltaMovement(velocity);
		ViewSpace.faceAlong(projectile, velocity);
		world.addFreshEntity(projectile);
		projectile.tudursvehiclemod$forceLoadSpawnChunk();
		if (def.effect().type() != ThrowableDefinition.Type.NONE) {
			PENDING_EFFECTS.put(projectile.getUUID(), def.effect());
		}
		stats.sound().ifPresent(sound -> HandheldCombat.playSound(player, sound, origin, stats.soundVolume(),
				stats.soundPitch(), stats.soundPitchRandom()));
		if (!player.isCreative()) {
			stack.shrink(1);
		}
	}

	/** A thrown projectile left the world. Its effect happens if it was used up (fuse or impact); one
	 * merely unloaded with its chunk keeps it for when it's loaded again. */
	public static void onProjectileRemoved(Entity entity, ServerLevel world) {
		Entity.RemovalReason reason = entity.getRemovalReason();
		if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK) {
			return;
		}
		ThrowableDefinition.Effect effect = PENDING_EFFECTS.remove(entity.getUUID());
		if (effect == null || (reason != Entity.RemovalReason.DISCARDED && reason != Entity.RemovalReason.KILLED)) {
			return;
		}
		Vec3 pos = entity.position();
		effect.sound().ifPresent(sound -> HandheldCombat.playSoundAt(world, sound, pos, 1.5f, 1.0f, 0.1f));
		switch (effect.type()) {
			case SMOKE, SIGNAL, GAS -> SmokeCloudEntity.create(world, pos, effect.radius(), effect.durationTicks(), effect.rgb(),
					SmokeCloudEntity.Kind.valueOf(effect.type().name()));
			case FLASH -> flash(world, pos, effect);
			case INCENDIARY -> ignite(world, pos, effect);
			default -> {
			}
		}
	}

	/** Players who can see the flash get a white-out (FlashPayload), strongest up close and when
	 * looking straight at it; mobs nearby lose their target and are slowed. */
	private static void flash(ServerLevel world, Vec3 pos, ThrowableDefinition.Effect effect) {
		double radius = effect.radius();
		for (ServerPlayer player : world.getPlayers(candidate -> candidate.distanceToSqr(pos) <= radius * radius)) {
			Vec3 eye = player.getEyePosition();
			HitResult hit = world.clip(new ClipContext(pos, eye, ClipContext.Block.VISUAL,
					ClipContext.Fluid.NONE, player));
			if (hit.getType() != HitResult.Type.MISS) {
				continue;
			}
			double distance = eye.distanceTo(pos);
			double facing = distance < 1.0E-3 ? 1.0 : player.getViewVector(1.0f).dot(pos.subtract(eye).scale(1.0 / distance));
			double closeness = 1.0 - distance / radius;
			float intensity = (float) Mth.clamp(closeness * (facing > 0 ? 0.5 + 0.5 * facing : 0.25), 0.0, 1.0);
			if (intensity > 0.05f) {
				ServerPlayNetworking.send(player, new FlashPayload(intensity, Math.round(effect.durationTicks() * intensity)));
			}
		}
		AABB area = new AABB(pos, pos).inflate(radius);
		for (Mob mob : world.getEntitiesOfClass(Mob.class, area, mob -> mob.distanceToSqr(pos) <= radius * radius)) {
			mob.setTarget(null);
			mob.getNavigation().stop();
			mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, effect.durationTicks(), 3));
		}
	}

	/** Fire on every open spot above solid ground within the radius, and everything living in it alight. */
	private static void ignite(ServerLevel world, Vec3 pos, ThrowableDefinition.Effect effect) {
		int radius = Math.max(1, Math.round(effect.radius()));
		BlockPos center = BlockPos.containing(pos);
		BlockState fire = Blocks.FIRE.defaultBlockState();
		for (BlockPos target : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 2, radius))) {
			if (target.distSqr(center) > (double) radius * radius) {
				continue;
			}
			BlockPos below = target.below();
			if (world.getBlockState(target).isAir()
					&& world.getBlockState(below).isFaceSturdy(world, below, Direction.UP)
					&& world.random.nextFloat() < 0.7f) {
				world.setBlockAndUpdate(target, fire);
			}
		}
		AABB area = new AABB(pos, pos).inflate(effect.radius());
		for (LivingEntity living : world.getEntitiesOfClass(LivingEntity.class, area,
				living -> living.distanceToSqr(pos) <= effect.radius() * effect.radius())) {
			living.igniteForSeconds(Math.max(1f, effect.durationTicks() / 20f));
		}
	}
}
