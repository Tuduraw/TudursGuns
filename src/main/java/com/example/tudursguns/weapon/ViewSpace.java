package com.example.tudursguns.weapon;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3fc;

/** Points and directions in a shooter's view - [right, up, forward] from the eye, as the definitions'
 * muzzle_offset and effect offsets are written. */
final class ViewSpace {

	private ViewSpace() {
	}

	/** Whether the hand is on the player's right (offsets are mirrored for the other side). */
	static boolean rightSide(LivingEntity player, Hand hand) {
		return (hand == Hand.MAIN_HAND) == (player.getMainArm() == Arm.RIGHT);
	}

	/** The player's right, level with the ground. */
	static Vec3d right(LivingEntity player) {
		double yaw = Math.toRadians(player.getYaw());
		return new Vec3d(-Math.cos(yaw), 0.0, -Math.sin(yaw));
	}

	/** The eye plus offset [right, up, forward] (right mirrored on the left side). */
	static Vec3d point(LivingEntity player, Vector3fc offset, boolean rightSide) {
		return player.getEyePos().add(right(player).multiply(rightSide ? offset.x() : -offset.x()))
				.add(0.0, offset.y(), 0.0)
				.add(player.getRotationVec(1.0f).multiply(offset.z()));
	}

	/** target, or the eye if a block is in the way - so nothing is fired or thrown through a wall. */
	static Vec3d clearOfBlocks(LivingEntity player, Vec3d target) {
		Vec3d eye = player.getEyePos();
		HitResult hit = player.getEntityWorld().raycast(new RaycastContext(eye, target,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? target : eye;
	}

	/** Turns an entity (a projectile) to face along velocity. */
	static void faceAlong(Entity entity, Vec3d velocity) {
		double speed = velocity.length();
		if (speed > 1.0E-6) {
			entity.setAngles((float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z)),
					(float) Math.toDegrees(-Math.asin(MathHelper.clamp(velocity.y / speed, -1.0, 1.0))));
		}
	}
}
