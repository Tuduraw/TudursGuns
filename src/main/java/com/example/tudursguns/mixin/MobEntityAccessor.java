package com.example.tudursguns.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A mob's target goals - SoldierEnemies adds one to monsters. */
@Mixin(Mob.class)
public interface MobEntityAccessor {

	@Accessor("targetSelector")
	GoalSelector tudursguns$getTargetSelector();
}
