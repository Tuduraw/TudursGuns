package com.example.tudursguns.soldier;

import com.example.tudursguns.TudursGunsConfig;
import com.example.tudursguns.mixin.ActiveTargetGoalAccessor;
import com.example.tudursguns.mixin.MobEntityAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/** Makes monsters go for posted soldiers as they go for players: every monster that hunts players
 * with an ActiveTargetGoal (zombies, skeletons, spiders, creepers...) gets a second one, at the same
 * priority, for FriendlySoldierEntity. Neutral monsters (endermen, zombified piglins...) and monsters
 * run by a brain instead of goals (piglins, wardens...) are left alone. Server config
 * monsters_target_soldiers turns this off. */
public final class SoldierEnemies {

	private SoldierEnemies() {
	}

	/** Marks the goal this class adds, so it's added only once. */
	private static final class TargetSoldierGoal extends NearestAttackableTargetGoal<FriendlySoldierEntity> {
		TargetSoldierGoal(Mob mob) {
			super(mob, FriendlySoldierEntity.class, true);
		}
	}

	/** ServerEntityEvents.ENTITY_LOAD */
	public static void onEntityLoad(Entity entity, ServerLevel world) {
		if (!TudursGunsConfig.get().monsters_target_soldiers || !(entity instanceof Mob mob) || !(mob instanceof Enemy)
				|| mob instanceof NeutralMob || mob instanceof EnemySoldierEntity) {
			return;
		}
		GoalSelector targets = ((MobEntityAccessor) mob).tudursguns$getTargetSelector();
		int priority = -1;
		for (WrappedGoal goal : targets.getAvailableGoals()) {
			if (goal.getGoal() instanceof TargetSoldierGoal) {
				return;
			}
			if (goal.getGoal() instanceof NearestAttackableTargetGoal<?> active
					&& ((ActiveTargetGoalAccessor) active).tudursguns$getTargetClass() == Player.class) {
				priority = goal.getPriority();
			}
		}
		if (priority >= 0) {
			targets.addGoal(priority, new TargetSoldierGoal(mob));
		}
	}
}
