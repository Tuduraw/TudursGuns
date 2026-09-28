package com.example.tudursguns.soldier;

import com.example.tudursguns.block.SoldierPostBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.Monster;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.List;

/** Picks a post's soldier's target: the nearest monster it can see within its engage range that is
 * also within engage range of the route (or of the post, with no route). The soldier lets a target
 * go once it has been drawn LEASH blocks beyond that, or can't fight any more (no food, no rounds,
 * stood down). */
public class SoldierTargetGoal extends Goal {

	private static final int SEARCH_INTERVAL_TICKS = 10;
	private static final int RETARGET_INTERVAL_TICKS = 40;
	private static final double LEASH = 16.0;

	private final FriendlySoldierEntity soldier;
	private int cooldown;

	public SoldierTargetGoal(FriendlySoldierEntity soldier) {
		this.soldier = soldier;
		this.setControls(EnumSet.of(Control.TARGET));
	}

	@Override
	public boolean canStart() {
		if (--this.cooldown > 0 || !this.soldier.canFight()) {
			return false;
		}
		this.cooldown = SEARCH_INTERVAL_TICKS;
		return find() != null;
	}

	@Override
	public void start() {
		this.soldier.setTarget(find());
		this.cooldown = RETARGET_INTERVAL_TICKS;
	}

	@Override
	public boolean shouldContinue() {
		LivingEntity target = this.soldier.getTarget();
		SoldierPostBlockEntity post = this.soldier.post();
		if (target == null || !target.isAlive() || post == null || !this.soldier.canFight()) {
			return false;
		}
		double range = this.soldier.engageRange() + LEASH;
		return this.soldier.distanceTo(target) <= range && distanceToRoute(post, target.getEntityPos()) <= range;
	}

	@Override
	public void tick() {
		// Switch to something nearer now and then.
		if (--this.cooldown <= 0) {
			this.cooldown = RETARGET_INTERVAL_TICKS;
			LivingEntity nearer = find();
			if (nearer != null) {
				this.soldier.setTarget(nearer);
			}
		}
	}

	@Override
	public void stop() {
		this.soldier.setTarget(null);
		this.cooldown = SEARCH_INTERVAL_TICKS;
	}

	private LivingEntity find() {
		SoldierPostBlockEntity post = this.soldier.post();
		if (post == null) {
			return null;
		}
		double range = this.soldier.engageRange();
		List<LivingEntity> candidates = this.soldier.getEntityWorld().getEntitiesByClass(LivingEntity.class,
				new Box(this.soldier.getBlockPos()).expand(range),
				entity -> entity instanceof Monster && entity.isAlive() && !entity.isInvisible()
						&& this.soldier.squaredDistanceTo(entity) <= range * range
						&& distanceToRoute(post, entity.getEntityPos()) <= range
						&& this.soldier.canSee(entity));
		LivingEntity nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (LivingEntity candidate : candidates) {
			double distance = this.soldier.squaredDistanceTo(candidate);
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = candidate;
			}
		}
		return nearest;
	}

	/** Distance from pos to the route walked as a loop, or to the post when there's no route. */
	private static double distanceToRoute(SoldierPostBlockEntity post, Vec3d pos) {
		BlockPos postPos = post.getPos();
		List<SoldierWaypoint> route = post.route();
		if (route.isEmpty()) {
			return pos.distanceTo(Vec3d.ofCenter(postPos));
		}
		double best = Double.MAX_VALUE;
		for (int i = 0; i < route.size(); i++) {
			Vec3d a = Vec3d.ofCenter(route.get(i).absolute(postPos));
			Vec3d b = Vec3d.ofCenter(route.get((i + 1) % route.size()).absolute(postPos));
			best = Math.min(best, distanceToSegment(pos, a, b));
		}
		return best;
	}

	private static double distanceToSegment(Vec3d p, Vec3d a, Vec3d b) {
		Vec3d ab = b.subtract(a);
		double lengthSquared = ab.lengthSquared();
		double t = lengthSquared < 1.0E-6 ? 0.0 : Math.max(0.0, Math.min(1.0, p.subtract(a).dotProduct(ab) / lengthSquared));
		return p.distanceTo(a.add(ab.multiply(t)));
	}
}
