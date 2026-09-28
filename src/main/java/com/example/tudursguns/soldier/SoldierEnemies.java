package com.example.tudursguns.soldier;

import com.example.tudursguns.TudursGunsConfig;
import com.example.tudursguns.mixin.ActiveTargetGoalAccessor;
import com.example.tudursguns.mixin.MobEntityAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.PrioritizedGoal;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** Makes monsters go for posted soldiers as they go for players: every monster that hunts players
 * with an ActiveTargetGoal (zombies, skeletons, spiders, creepers...) gets a second one, at the same
 * priority, for FriendlySoldierEntity. Neutral monsters (endermen, zombified piglins...) and monsters
 * run by a brain instead of goals (piglins, wardens...) are left alone. Server config
 * monsters_target_soldiers turns this off. */
public final class SoldierEnemies {

	private SoldierEnemies() {
	}

	/** Marks the goal this class adds, so it's added only once. */
	private static final class TargetSoldierGoal extends ActiveTargetGoal<FriendlySoldierEntity> {
		TargetSoldierGoal(MobEntity mob) {
			super(mob, FriendlySoldierEntity.class, true);
		}
	}

	/** ServerEntityEvents.ENTITY_LOAD */
	public static void onEntityLoad(Entity entity, ServerWorld world) {
		if (!TudursGunsConfig.get().monsters_target_soldiers || !(entity instanceof MobEntity mob) || !(mob instanceof Monster)
				|| mob instanceof Angerable || mob instanceof EnemySoldierEntity) {
			return;
		}
		GoalSelector targets = ((MobEntityAccessor) mob).tudursguns$getTargetSelector();
		int priority = -1;
		for (PrioritizedGoal goal : targets.getGoals()) {
			if (goal.getGoal() instanceof TargetSoldierGoal) {
				return;
			}
			if (goal.getGoal() instanceof ActiveTargetGoal<?> active
					&& ((ActiveTargetGoalAccessor) active).tudursguns$getTargetClass() == PlayerEntity.class) {
				priority = goal.getPriority();
			}
		}
		if (priority >= 0) {
			targets.add(priority, new TargetSoldierGoal(mob));
		}
	}
}
