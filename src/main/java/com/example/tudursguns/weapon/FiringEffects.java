package com.example.tudursguns.weapon;

import com.example.tudursguns.entity.CosmeticProjectile;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.HandheldEffects;
import com.example.tudursvehiclemod.asset.CartridgeConfig;
import com.example.tudursvehiclemod.asset.MuzzleFlashConfig;
import com.example.tudursvehiclemod.asset.WeaponStats;
import com.example.tudursvehiclemod.asset.WeaponType;
import com.example.tudursvehiclemod.entity.projectile.VehicleModelProjectileEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/** What a handheld shot or reload looks like around the weapon (a definition's "effects" - see
 * HandheldEffects): the muzzle flash, spent cartridges and empty magazines.
 *
 * Cartridges and magazines are Tudur's Vehicle Mod's own model projectile - the same thing its
 * SetCartridge uses on vehicles - with no damage or explosion, marked cosmetic so it hits no
 * entity and isn't saved (see CosmeticProjectile). It falls, bounces off blocks (Bound) and
 * disappears lifetime ticks after it first lands (DelayFuse).
 *
 * Nothing set in the definition falls back to the weapon file's own AddMuzzleFlash /
 * AddMuzzleFlashSmoke / SetCartridge, then to the weapon type's default. Server side only. */
public final class FiringEffects {

	private FiringEffects() {
	}

	/** Most rounds a "reload" ejection throws out at once. */
	private static final int MAX_RELOAD_COUNT = 12;

	// ---------------------------------------------------------------- events

	/** A shot left the weapon at muzzle. */
	public static void onFire(LivingEntity player, Hand hand, HandheldDefinition def, WeaponStats stats,
			WeaponModifiers modifiers, Vec3d muzzle) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		float flashScale = modifiers.muzzleFlashMultiplier();
		HandheldEffects.MuzzleFlash flash = muzzleFlash(def, stats);
		if (flash.enabled() && flashScale > 0f) {
			spawnFlash(world, player, flash, flashScale, muzzle);
		}
		if (def.effects().muzzleFlash().isEmpty() && flashScale > 0f) {
			// The weapon file's AddMuzzleFlashSmoke, drawn as a vehicle draws it.
			stats.muzzleFlashSmoke().ifPresent(smoke -> {
				Vec3d pos = muzzle.add(player.getRotationVec(1.0f).multiply(smoke.distanceFromMuzzle()));
				DustParticleEffect effect = new DustParticleEffect(smoke.argbColor() & 0xFFFFFF, Math.min(4f, smoke.size() * 0.2f));
				world.spawnParticles(effect, true, true, pos.x, pos.y, pos.z, smoke.count(),
						smoke.spreadRange() * 0.1, smoke.spreadRange() * 0.1, smoke.spreadRange() * 0.1, 0.02);
			});
		}
		for (HandheldEffects.Ejection ejection : List.of(cartridge(def, stats), magazine(def, stats))) {
			if (ejection.enabled() && ejection.on() == HandheldEffects.Trigger.FIRE) {
				schedule(player, hand, def, ejection, Math.max(1, ejection.count()));
			}
		}
	}

	/** A reload started. spent: rounds fired since the magazine was last full. */
	public static void onReload(LivingEntity player, ItemStack stack, HandheldDefinition def, WeaponStats stats, int spent) {
		Hand hand = player.getOffHandStack() == stack ? Hand.OFF_HAND : Hand.MAIN_HAND;
		for (HandheldEffects.Ejection ejection : List.of(cartridge(def, stats), magazine(def, stats))) {
			if (!ejection.enabled() || ejection.on() != HandheldEffects.Trigger.RELOAD) {
				continue;
			}
			int count = ejection.count() > 0 ? ejection.count() : Math.min(MAX_RELOAD_COUNT, spent);
			if (count > 0) {
				schedule(player, hand, def, ejection, count);
			}
		}
	}

	// ---------------------------------------------------------------- what applies

	private static boolean isGun(WeaponStats stats) {
		return stats.weaponType() == WeaponType.MACHINE_GUN;
	}

	private static boolean isLauncher(WeaponStats stats) {
		return switch (stats.weaponType()) {
			case ROCKET, MK_ROCKET, AS_MISSILE, AA_MISSILE, AT_MISSILE, MISSILE, TV_MISSILE -> true;
			default -> false;
		};
	}

	/** The definition's muzzle_flash, else the weapon file's AddMuzzleFlash, else the type's default. */
	private static HandheldEffects.MuzzleFlash muzzleFlash(HandheldDefinition def, WeaponStats stats) {
		if (def.effects().muzzleFlash().isPresent()) {
			return def.effects().muzzleFlash().get();
		}
		if (stats.muzzleFlash().isPresent()) {
			MuzzleFlashConfig file = stats.muzzleFlash().get();
			return new HandheldEffects.MuzzleFlash(true, MathHelper.clamp(file.size(), 0.01f, 4f), file.argbColor() & 0xFFFFFF,
					Math.max(1, file.displayTicks()), 0, file.distanceFromMuzzle());
		}
		return isGun(stats) || isLauncher(stats) ? HandheldEffects.MuzzleFlash.DEFAULT : HandheldEffects.MuzzleFlash.OFF;
	}

	/** The definition's cartridge, else the weapon file's SetCartridge, else the type's default. */
	private static HandheldEffects.Ejection cartridge(HandheldDefinition def, WeaponStats stats) {
		if (def.effects().cartridge().isPresent()) {
			return def.effects().cartridge().get();
		}
		if (stats.cartridge().isPresent()) {
			return fromWeaponFile(stats.cartridge().get(), stats);
		}
		return isGun(stats) ? HandheldEffects.Ejection.CARTRIDGE : HandheldEffects.Ejection.OFF;
	}

	/** The definition's magazine, else the type's default (guns holding more than one round). */
	private static HandheldEffects.Ejection magazine(HandheldDefinition def, WeaponStats stats) {
		if (def.effects().magazine().isPresent()) {
			return def.effects().magazine().get();
		}
		return isGun(stats) && stats.magazineSize() > 1 ? HandheldEffects.Ejection.MAGAZINE : HandheldEffects.Ejection.OFF;
	}

	/** SetCartridge = model, acceleration, yaw, pitch, scale, gravity, bound - the vehicle mod's own
	 * model convention (models/obj/bullet_<model>.obj in the weapon file's namespace) and directions
	 * (yaw: positive = left of the shot; pitch: positive = down), thrown from the muzzle. */
	private static HandheldEffects.Ejection fromWeaponFile(CartridgeConfig cart, WeaponStats stats) {
		String namespace = stats.bulletModel().map(Identifier::getNamespace)
				.orElseGet(() -> stats.bulletTexture().map(Identifier::getNamespace).orElse("minecraft"));
		double yaw = Math.toRadians(cart.yawDegrees());
		double pitch = Math.toRadians(cart.pitchDegrees());
		Vector3f velocity = new Vector3f((float) (-Math.sin(yaw) * Math.cos(pitch)), (float) -Math.sin(pitch),
				(float) (Math.cos(yaw) * Math.cos(pitch))).mul(cart.acceleration());
		HandheldEffects.Ejection defaults = HandheldEffects.Ejection.CARTRIDGE;
		return new HandheldEffects.Ejection(true, defaults.type(),
				Optional.of(Identifier.of(namespace, "models/obj/bullet_" + cart.modelName() + ".obj")),
				Optional.of(Identifier.of(namespace, "textures/vehicle/bullet_" + cart.modelName() + ".png")),
				Math.max(0.01f, cart.modelScale()), Optional.of(new Vector3f()), velocity, defaults.velocityRandom(),
				Math.max(0f, cart.gravity()), MathHelper.clamp(cart.bounce(), 0f, 1f), HandheldEffects.Trigger.FIRE, 0, 1,
				defaults.lifetime());
	}

	// ---------------------------------------------------------------- spawning

	private static void spawnFlash(ServerWorld world, LivingEntity player, HandheldEffects.MuzzleFlash flash, float scale,
			Vec3d muzzle) {
		Vec3d pos = muzzle.add(player.getRotationVec(1.0f).multiply(flash.distance()));
		if (flash.count() > 0) {
			DustParticleEffect effect = new DustParticleEffect(flash.color(), MathHelper.clamp(flash.size() * scale, 0.01f, 4f));
			world.spawnParticles(effect, true, true, pos.x, pos.y, pos.z, flash.count(), 0.02, 0.02, 0.02, 0.0);
		}
		if (flash.smoke() > 0) {
			world.spawnParticles(ParticleTypes.SMOKE, true, false, pos.x, pos.y, pos.z, flash.smoke(), 0.03, 0.03, 0.03, 0.01);
		}
	}

	private static void schedule(LivingEntity player, Hand hand, HandheldDefinition def, HandheldEffects.Ejection ejection,
			int count) {
		boolean rightSide = ViewSpace.rightSide(player, hand);
		Vector3f muzzleOffset = new Vector3f(def.muzzleOffset());
		DelayedTasks.schedule(player, ejection.delay(), target -> eject(target, rightSide, muzzleOffset, ejection, count));
	}

	/** Where it comes out when the definition doesn't say: the ejection port (cartridges) or the
	 * magazine well (magazines) - a little way back from the muzzle and at least a little to the side
	 * of the view (a muzzle_offset is often straight ahead of the eye, where the weapon is when
	 * aiming), so it doesn't appear in the middle of the screen. */
	private static Vector3f defaultOffset(HandheldEffects.Ejection ejection, Vector3f muzzleOffset) {
		boolean magazine = "magazine".equalsIgnoreCase(ejection.type()) && ejection.model().isEmpty();
		float side = Math.max(muzzleOffset.x(), 0.12f);
		return magazine
				? new Vector3f(side * 0.8f, muzzleOffset.y() - 0.25f, muzzleOffset.z() * 0.35f)
				: new Vector3f(side, muzzleOffset.y() - 0.08f, muzzleOffset.z() * 0.35f);
	}

	private static void eject(LivingEntity player, boolean rightSide, Vector3f muzzleOffset, HandheldEffects.Ejection ejection,
			int count) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		Vec3d forward = player.getRotationVec(1.0f);
		Vec3d right = ViewSpace.right(player);
		Vec3d up = right.crossProduct(forward).normalize();
		if (up.y < 0) {
			up = up.multiply(-1.0);
		}
		double side = rightSide ? 1.0 : -1.0;
		Vector3f offset = ejection.offset().orElseGet(() -> defaultOffset(ejection, muzzleOffset));
		Vec3d origin = ViewSpace.point(player, offset, rightSide);
		Vector3f v = ejection.velocity();
		Vec3d baseVelocity = right.multiply(v.x() * side).add(up.multiply(v.y())).add(forward.multiply(v.z()))
				.add(player.getVelocity());
		for (int i = 0; i < count; i++) {
			VehicleModelProjectileEntity entity = new VehicleModelProjectileEntity(world, player, new ItemStack(Items.IRON_NUGGET),
					0f, ejection.gravity(), 0f, false, false, ejection.modelId(), ejection.textureId(), ejection.scale());
			((CosmeticProjectile) entity).tudursguns$setCosmetic(true);
			entity.setPosition(origin.x, origin.y, origin.z);
			double r = ejection.velocityRandom();
			Vec3d velocity = baseVelocity.add((world.random.nextDouble() * 2 - 1) * r, (world.random.nextDouble() * 2 - 1) * r,
					(world.random.nextDouble() * 2 - 1) * r);
			entity.setVelocity(velocity);
			ViewSpace.faceAlong(entity, velocity);
			entity.tudursvehiclemod$setBounceStrength(ejection.bounce());
			// Gone lifetime ticks after landing, and in any case a while after that.
			entity.tudursvehiclemod$setFuseTicks(ejection.lifetime(), ejection.lifetime() + 200);
			world.spawnEntity(entity);
		}
	}
}
