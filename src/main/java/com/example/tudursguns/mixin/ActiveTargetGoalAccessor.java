package com.example.tudursguns.mixin;

import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** What an ActiveTargetGoal hunts - SoldierEnemies looks for monsters that hunt players. */
@Mixin(NearestAttackableTargetGoal.class)
public interface ActiveTargetGoalAccessor {

	@Accessor("targetType")
	Class<?> tudursguns$getTargetClass();
}
