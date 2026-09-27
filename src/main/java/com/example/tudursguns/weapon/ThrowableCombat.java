package com.example.tudursguns.weapon;

import com.example.tudursguns.entity.SmokeCloudEntity;
import com.example.tudursguns.handheld.ThrowableDefinition;
import com.example.tudursguns.network.FlashPayload;
import com.example.tudursvehiclemod.asset.WeaponStats;
import com.example.tudursvehiclemod.asset.WeaponStatsLoader;
import com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity;
import com.example.tudursvehiclemod.entity.projectile.WeaponProjectileFactory;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

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

	public static void forget(UUID playerId) {
		PIN_PULLED_AT.remove(playerId);
	}

	public static void pullPin(ServerPlayerEntity player, ThrowableDefinition def) {
		PIN_PULLED_AT.put(player.getUuid(), player.getEntityWorld().getTime());
		def.pinSound().ifPresent(sound -> HandheldCombat.playSound(player, sound, player.getEyePos(), 0.6f, 1.0f, 0.05f));
	}

	/** Ticks the held throwable has been cooking (0 if not cookable or no pin pulled). */
	public static int cookedTicks(ServerPlayerEntity player, ThrowableDefinition def) {
		Long pulled = PIN_PULLED_AT.get(player.getUuid());
		if (!def.cooks() || pulled == null) {
			return 0;
		}
		return (int) Math.max(0, player.getEntityWorld().getTime() - pulled);
	}

	/** Called every tick while held with the pin out: a cookable throwable held past its fuse goes off
	 * in the thrower's hand. Returns true if it did. */
	public static boolean checkCookedOff(ServerPlayerEntity player, ItemStack stack, ThrowableDefinition def) {
		if (!def.cooks() || cookedTicks(player, def) < def.fuseTicks()) {
			return false;
		}
		launch(player, stack, def, Vec3d.ZERO, player.getEyePos().add(0, -0.3, 0), 1);
		PIN_PULLED_AT.remove(player.getUuid());
		return true;
	}

	/** Releases the throw: overhand, or underhand (a gentle lob) while the aim key is held. */
	public static void throwHeld(ServerPlayerEntity player, ItemStack stack, ThrowableDefinition def) {
		boolean underhand = HandheldCombat.isAimKeyHeld(player);
		int cooked = cookedTicks(player, def);
		PIN_PULLED_AT.remove(player.getUuid());
		float speed = underhand ? def.underhandVelocity() : def.throwVelocity();
		Vec3d direction = underhand ? lobDirection(player) : player.getRotationVec(1.0f);
		Vec3d velocity = direction.multiply(speed).add(player.getVelocity());
		int remainingFuse = def.fuseTicks() > 0 ? Math.max(1, def.fuseTicks() - cooked) : -1;
		launch(player, stack, def, velocity, throwOrigin(player), remainingFuse);
	}

	/** Underhand: the view direction tilted 20 degrees down (a short, low lob at the feet ahead). */
	private static Vec3d lobDirection(ServerPlayerEntity player) {
		float pitch = MathHelper.clamp(player.getPitch() + 20f, -90f, 90f);
		return Vec3d.fromPolar(pitch, player.getYaw());
	}

	/** Slightly in front of and below the eyes - pulled back to the eyes if a block is in the way. */
	public static Vec3d throwOrigin(ServerPlayerEntity player) {
		Vec3d eye = player.getEyePos();
		Vec3d target = eye.add(player.getRotationVec(1.0f).multiply(0.4)).add(0, -0.1, 0);
		HitResult hit = player.getEntityWorld().raycast(new RaycastContext(eye, target,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? target : eye;
	}

	/** Spawns the thrown projectile and takes one throwable from the stack (not in creative). */
	private static void launch(ServerPlayerEntity player, ItemStack stack, ThrowableDefinition def, Vec3d velocity, Vec3d origin,
			int timeFuseTicks) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
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
		projectile.setPosition(origin.x, origin.y, origin.z);
		projectile.setVelocity(velocity);
		double speed = velocity.length();
		if (speed > 1.0E-6) {
			projectile.setAngles((float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z)),
					(float) Math.toDegrees(-Math.asin(MathHelper.clamp(velocity.y / speed, -1.0, 1.0))));
		}
		world.spawnEntity(projectile);
		projectile.tudursvehiclemod$forceLoadSpawnChunk();
		if (def.effect().type() != ThrowableDefinition.Type.NONE) {
			PENDING_EFFECTS.put(projectile.getUuid(), def.effect());
		}
		stats.sound().ifPresent(sound -> HandheldCombat.playSound(player, sound, origin, stats.soundVolume(),
				stats.soundPitch(), stats.soundPitchRandom()));
		if (!player.isCreative()) {
			stack.decrement(1);
		}
	}

	/** A thrown projectile left the world. Its effect happens if it was used up (fuse or impact),
	 * not if it was merely unloaded with its chunk. */
	public static void onProjectileRemoved(Entity entity, ServerWorld world) {
		ThrowableDefinition.Effect effect = PENDING_EFFECTS.remove(entity.getUuid());
		if (effect == null) {
			return;
		}
		Entity.RemovalReason reason = entity.getRemovalReason();
		if (reason != Entity.RemovalReason.DISCARDED && reason != Entity.RemovalReason.KILLED) {
			return;
		}
		Vec3d pos = entity.getEntityPos();
		effect.sound().ifPresent(sound -> HandheldCombat.playSoundAt(world, sound, pos, 1.5f, 1.0f, 0.1f));
		switch (effect.type()) {
			case SMOKE -> SmokeCloudEntity.create(world, pos, effect.radius(), effect.durationTicks(), effect.rgb(), SmokeCloudEntity.Kind.SMOKE);
			case SIGNAL -> SmokeCloudEntity.create(world, pos, effect.radius(), effect.durationTicks(), effect.rgb(), SmokeCloudEntity.Kind.SIGNAL);
			case GAS -> SmokeCloudEntity.create(world, pos, effect.radius(), effect.durationTicks(), effect.rgb(), SmokeCloudEntity.Kind.GAS);
			case FLASH -> flash(world, pos, effect);
			case INCENDIARY -> ignite(world, pos, effect);
			default -> {
			}
		}
	}

	/** Players who can see the flash get a white-out (FlashPayload), strongest up close and when
	 * looking straight at it; mobs nearby lose their target and are slowed. */
	private static void flash(ServerWorld world, Vec3d pos, ThrowableDefinition.Effect effect) {
		double radius = effect.radius();
		for (ServerPlayerEntity player : world.getPlayers(candidate -> candidate.squaredDistanceTo(pos) <= radius * radius)) {
			Vec3d eye = player.getEyePos();
			HitResult hit = world.raycast(new RaycastContext(pos, eye, RaycastContext.ShapeType.VISUAL,
					RaycastContext.FluidHandling.NONE, player));
			if (hit.getType() != HitResult.Type.MISS) {
				continue;
			}
			double distance = eye.distanceTo(pos);
			double facing = distance < 1.0E-3 ? 1.0 : player.getRotationVec(1.0f).dotProduct(pos.subtract(eye).multiply(1.0 / distance));
			double closeness = 1.0 - distance / radius;
			float intensity = (float) MathHelper.clamp(closeness * (facing > 0 ? 0.5 + 0.5 * facing : 0.25), 0.0, 1.0);
			if (intensity > 0.05f) {
				ServerPlayNetworking.send(player, new FlashPayload(intensity, Math.round(effect.durationTicks() * intensity)));
			}
		}
		Box area = new Box(pos, pos).expand(radius);
		for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, area, mob -> mob.squaredDistanceTo(pos) <= radius * radius)) {
			mob.setTarget(null);
			mob.getNavigation().stop();
			mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, effect.durationTicks(), 3));
		}
	}

	/** Fire on every open spot above solid ground within the radius, and everything living in it alight. */
	private static void ignite(ServerWorld world, Vec3d pos, ThrowableDefinition.Effect effect) {
		int radius = Math.max(1, Math.round(effect.radius()));
		BlockPos center = BlockPos.ofFloored(pos);
		BlockState fire = Blocks.FIRE.getDefaultState();
		for (BlockPos target : BlockPos.iterate(center.add(-radius, -2, -radius), center.add(radius, 2, radius))) {
			if (target.getSquaredDistance(center) > (double) radius * radius) {
				continue;
			}
			BlockPos below = target.down();
			if (world.getBlockState(target).isAir()
					&& world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP)
					&& world.random.nextFloat() < 0.7f) {
				world.setBlockState(target, fire);
			}
		}
		Box area = new Box(pos, pos).expand(effect.radius());
		for (LivingEntity living : world.getEntitiesByClass(LivingEntity.class, area,
				living -> living.squaredDistanceTo(pos) <= effect.radius() * effect.radius())) {
			living.setOnFireFor(Math.max(1f, effect.durationTicks() / 20f));
		}
	}
}
