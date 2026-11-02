package com.example.tudursguns.soldier;

import com.example.tudursguns.block.SoldierPostBlockEntity;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
		this.setFlags(EnumSet.of(Flag.TARGET));
	}

	@Override
	public boolean canUse() {
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
	public boolean canContinueToUse() {
		LivingEntity target = this.soldier.getTarget();
		SoldierPostBlockEntity post = this.soldier.post();
		if (target == null || !target.isAlive() || post == null || !this.soldier.canFight()) {
			return false;
		}
		double range = this.soldier.engageRange() + LEASH;
		return this.soldier.distanceTo(target) <= range && distanceToRoute(post, target.position()) <= range;
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
		List<LivingEntity> candidates = this.soldier.level().getEntitiesOfClass(LivingEntity.class,
				new AABB(this.soldier.blockPosition()).inflate(range),
				entity -> entity instanceof Enemy && entity.isAlive() && !entity.isInvisible()
						&& this.soldier.distanceToSqr(entity) <= range * range
						&& distanceToRoute(post, entity.position()) <= range
						&& this.soldier.hasLineOfSight(entity));
		LivingEntity nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (LivingEntity candidate : candidates) {
			double distance = this.soldier.distanceToSqr(candidate);
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = candidate;
			}
		}
		return nearest;
	}

	/** Distance from pos to the route walked as a loop, or to the post when there's no route. */
	private static double distanceToRoute(SoldierPostBlockEntity post, Vec3 pos) {
		BlockPos postPos = post.getBlockPos();
		List<SoldierWaypoint> route = post.route();
		if (route.isEmpty()) {
			return pos.distanceTo(Vec3.atCenterOf(postPos));
		}
		double best = Double.MAX_VALUE;
		for (int i = 0; i < route.size(); i++) {
			Vec3 a = Vec3.atCenterOf(route.get(i).absolute(postPos));
			Vec3 b = Vec3.atCenterOf(route.get((i + 1) % route.size()).absolute(postPos));
			best = Math.min(best, distanceToSegment(pos, a, b));
		}
		return best;
	}

	private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double lengthSquared = ab.lengthSqr();
		double t = lengthSquared < 1.0E-6 ? 0.0 : Math.max(0.0, Math.min(1.0, p.subtract(a).dot(ab) / lengthSquared));
		return p.distanceTo(a.add(ab.scale(t)));
	}
}
