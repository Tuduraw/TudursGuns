package com.example.tudursguns.client.mixin;

import com.example.tudursguns.client.AimController;
import com.example.tudursguns.client.render.AimRenderState;
import com.example.tudursguns.soldier.Soldier;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks a player's (or soldier's) render state as aiming / sprint-carrying a handheld weapon - BipedEntityModelAimMixin
 * turns that into the arm pose. HEAD also records it for the entity being updated, which the held
 * item's renderer reads while the state is built (ObjHandheldModelRenderer.getData). Render states
 * are reused between frames, so the marks are written every time (null when off). */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererAimMixin {

	@Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At("HEAD"))
	private void tudursguns$beginEntity(LivingEntity entity, LivingEntityRenderState state, float tickProgress, CallbackInfo ci) {
		AimRenderState.setEntityBeingUpdatedAims(entity instanceof PlayerEntity player ? AimController.isAiming(player)
				: entity instanceof Soldier soldier && soldier.isAimingWeapon());
		AimRenderState.setEntityBeingUpdatedSprintCarries(entity instanceof PlayerEntity player && AimController.isSprintCarrying(player));
	}

	@Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
	private void tudursguns$markAiming(LivingEntity entity, LivingEntityRenderState state, float tickProgress, CallbackInfo ci) {
		state.setData(AimRenderState.AIMING, AimRenderState.entityBeingUpdatedAims() ? Boolean.TRUE : null);
		state.setData(AimRenderState.SPRINT_CARRY, AimRenderState.entityBeingUpdatedSprintCarries() ? Boolean.TRUE : null);
	}
}
