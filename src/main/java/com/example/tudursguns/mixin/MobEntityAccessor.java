package com.example.tudursguns.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A mob's target goals - SoldierEnemies adds one to monsters. */
@Mixin(MobEntity.class)
public interface MobEntityAccessor {

	@Accessor("targetSelector")
	GoalSelector tudursguns$getTargetSelector();
}
