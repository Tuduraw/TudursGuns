package com.example.tudursguns.weapon;

import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/** Points and directions in a shooter's view - [right, up, forward] from the eye, as the definitions'
 * muzzle_offset and effect offsets are written. */
final class ViewSpace {

	private ViewSpace() {
	}

	/** Whether the hand is on the player's right (offsets are mirrored for the other side). */
	static boolean rightSide(LivingEntity player, InteractionHand hand) {
		return (hand == InteractionHand.MAIN_HAND) == (player.getMainArm() == HumanoidArm.RIGHT);
	}

	/** The player's right, level with the ground. */
	static Vec3 right(LivingEntity player) {
		double yaw = Math.toRadians(player.getYRot());
		return new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
	}

	/** The eye plus offset [right, up, forward] (right mirrored on the left side). */
	static Vec3 point(LivingEntity player, Vector3fc offset, boolean rightSide) {
		return player.getEyePosition().add(right(player).scale(rightSide ? offset.x() : -offset.x()))
				.add(0.0, offset.y(), 0.0)
				.add(player.getViewVector(1.0f).scale(offset.z()));
	}

	/** target, or the eye if a block is in the way - so nothing is fired or thrown through a wall. */
	static Vec3 clearOfBlocks(LivingEntity player, Vec3 target) {
		Vec3 eye = player.getEyePosition();
		HitResult hit = player.level().clip(new ClipContext(eye, target,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? target : eye;
	}

	/** Turns an entity (a projectile) to face along velocity. */
	static void faceAlong(Entity entity, Vec3 velocity) {
		double speed = velocity.length();
		if (speed > 1.0E-6) {
			entity.absSnapRotationTo((float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z)),
					(float) Math.toDegrees(-Math.asin(Mth.clamp(velocity.y / speed, -1.0, 1.0))));
		}
	}
}
