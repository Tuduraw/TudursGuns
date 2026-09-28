package com.example.tudursguns.mixin;

import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** What an ActiveTargetGoal hunts - SoldierEnemies looks for monsters that hunt players. */
@Mixin(ActiveTargetGoal.class)
public interface ActiveTargetGoalAccessor {

	@Accessor("targetClass")
	Class<?> tudursguns$getTargetClass();
}
