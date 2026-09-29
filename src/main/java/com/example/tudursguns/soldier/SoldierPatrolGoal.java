package com.example.tudursguns.soldier;

import com.example.tudursguns.block.SoldierPostBlockEntity;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.List;

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
		this.setControls(EnumSet.of(Control.MOVE));
	}

	@Override
	public boolean canStart() {
		return this.soldier.getTarget() == null && this.soldier.post() != null;
	}

	@Override
	public boolean shouldContinue() {
		return canStart();
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
		Vec3d home = Vec3d.ofBottomCenter(post.getPos().up());
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
			this.index = nearest(route, post.getPos());
			this.waitUntil = -1;
			this.ticksOnPoint = 0;
		}
		SoldierWaypoint point = route.get(this.index);
		Vec3d target = Vec3d.ofBottomCenter(point.absolute(post.getPos()));
		long now = this.soldier.getEntityWorld().getTime();
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
	private void walkTo(Vec3d target) {
		if (--this.repathCooldown > 0 && !this.soldier.getNavigation().isIdle()) {
			return;
		}
		this.repathCooldown = REPATH_TICKS;
		Vec3d goal = target;
		if (this.soldier.getEntityPos().distanceTo(target) > STEP_DISTANCE) {
			Vec3d step = NoPenaltyTargeting.findTo(this.soldier, (int) STEP_DISTANCE, 7, target, Math.PI / 2);
			if (step != null) {
				goal = step;
			}
		}
		this.soldier.getNavigation().startMovingTo(goal.x, goal.y, goal.z, this.speed);
	}

	private double horizontalDistance(Vec3d target) {
		double dx = target.x - this.soldier.getX();
		double dz = target.z - this.soldier.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private int nearest(List<SoldierWaypoint> route, BlockPos postPos) {
		int best = 0;
		double bestDistance = Double.MAX_VALUE;
		for (int i = 0; i < route.size(); i++) {
			double distance = this.soldier.getBlockPos().getSquaredDistance(route.get(i).absolute(postPos));
			if (distance < bestDistance) {
				bestDistance = distance;
				best = i;
			}
		}
		return best;
	}
}
