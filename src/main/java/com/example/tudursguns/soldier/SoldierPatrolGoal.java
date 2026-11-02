package com.example.tudursguns.soldier;

import com.example.tudursguns.block.SoldierPostBlockEntity;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

/** A post's soldier's walking when it isn't fighting: round the post's route (in a loop, waiting at
 * each point as long as the point says), or near the post when there is no route. Walks back to the
 * post when it must (stood down, no food, magazine empty); once there, a stood-down soldier goes into
 * the post. */
public class SoldierPatrolGoal extends Goal {

	/** How close (horizontally) counts as having reached a point. */
	private static final double ARRIVE_DISTANCE = 1.5;
	/** How far from the post a soldier without a route may stand. */
	private static final double POST_RADIUS = 3.0;
	/** A point farther than this is walked to in steps (paths are only found so far). */
	private static final double STEP_DISTANCE = 24.0;
	/** A point not reached in this long is skipped. */
	private static final int GIVE_UP_TICKS = 600;
	private static final int REPATH_TICKS = 20;

	private final FriendlySoldierEntity soldier;
	private final double speed;
	private int index = -1;
	private long waitUntil = -1;
	private int ticksOnPoint;
	private int repathCooldown;

	public SoldierPatrolGoal(FriendlySoldierEntity soldier, double speed) {
		this.soldier = soldier;
		this.speed = speed;
		this.setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		return this.soldier.getTarget() == null && this.soldier.post() != null;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		// Pick the route up again at the nearest point (after a fight, say).
		this.index = -1;
		this.repathCooldown = 0;
	}

	@Override
	public void stop() {
		this.soldier.getNavigation().stop();
	}

	@Override
	public void tick() {
		SoldierPostBlockEntity post = this.soldier.post();
		if (post == null) {
			return;
		}
		Vec3 home = Vec3.atBottomCenterOf(post.getBlockPos().above());
		if (this.soldier.mustReturn() || this.soldier.outOfAmmo()) {
			if (horizontalDistance(home) <= POST_RADIUS) {
				this.soldier.getNavigation().stop();
				if (!post.isActive()) {
					post.recall(this.soldier);
				}
			} else {
				walkTo(home);
			}
			return;
		}
		List<SoldierWaypoint> route = post.route();
		if (route.isEmpty()) {
			if (horizontalDistance(home) > POST_RADIUS) {
				walkTo(home);
			}
			return;
		}
		if (this.index < 0 || this.index >= route.size()) {
			this.index = nearest(route, post.getBlockPos());
			this.waitUntil = -1;
			this.ticksOnPoint = 0;
		}
		SoldierWaypoint point = route.get(this.index);
		Vec3 target = Vec3.atBottomCenterOf(point.absolute(post.getBlockPos()));
		long now = this.soldier.level().getGameTime();
		// Height is ignored: a point recorded in the air (a route book made for drones) is reached below it.
		if (horizontalDistance(target) <= ARRIVE_DISTANCE) {
			this.soldier.getNavigation().stop();
			if (this.waitUntil < 0) {
				this.waitUntil = now + point.waitTicks();
			}
			if (now >= this.waitUntil) {
				next(route);
			}
			return;
		}
		if (++this.ticksOnPoint > GIVE_UP_TICKS) {
			next(route);
			return;
		}
		walkTo(target);
	}

	private void next(List<SoldierWaypoint> route) {
		this.index = (this.index + 1) % route.size();
		this.waitUntil = -1;
		this.ticksOnPoint = 0;
		this.repathCooldown = 0;
	}

	/** Heads for target, in steps when it's far - re-planning now and then rather than every tick. */
	private void walkTo(Vec3 target) {
		if (--this.repathCooldown > 0 && !this.soldier.getNavigation().isDone()) {
			return;
		}
		this.repathCooldown = REPATH_TICKS;
		Vec3 goal = target;
		if (this.soldier.position().distanceTo(target) > STEP_DISTANCE) {
			Vec3 step = DefaultRandomPos.getPosTowards(this.soldier, (int) STEP_DISTANCE, 7, target, Math.PI / 2);
			if (step != null) {
				goal = step;
			}
		}
		this.soldier.getNavigation().moveTo(goal.x, goal.y, goal.z, this.speed);
	}

	private double horizontalDistance(Vec3 target) {
		double dx = target.x - this.soldier.getX();
		double dz = target.z - this.soldier.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private int nearest(List<SoldierWaypoint> route, BlockPos postPos) {
		int best = 0;
		double bestDistance = Double.MAX_VALUE;
		for (int i = 0; i < route.size(); i++) {
			double distance = this.soldier.blockPosition().distSqr(route.get(i).absolute(postPos));
			if (distance < bestDistance) {
				bestDistance = distance;
				best = i;
			}
		}
		return best;
	}
}
