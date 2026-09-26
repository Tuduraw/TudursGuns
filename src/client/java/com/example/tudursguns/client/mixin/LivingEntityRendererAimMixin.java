package com.example.tudursguns.client.mixin;

import com.example.tudursguns.client.AimController;
import com.example.tudursguns.client.render.AimRenderState;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks a player's render state as "aiming a handheld weapon" - BipedEntityModelAimMixin turns that
 * into the raised two-handed pose. Render states are reused between frames, so it's written every
 * time (null when not aiming). */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererAimMixin {

	@Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At("HEAD"))
	private void tudursguns$beginEntity(LivingEntity entity, LivingEntityRenderState state, float tickProgress, CallbackInfo ci) {
		AimRenderState.setEntityBeingUpdatedAims(entity instanceof PlayerEntity player && AimController.isAiming(player));
	}

	@Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
	private void tudursguns$markAiming(LivingEntity entity, LivingEntityRenderState state, float tickProgress, CallbackInfo ci) {
		boolean aiming = entity instanceof PlayerEntity player && AimController.isAiming(player);
		state.setData(AimRenderState.AIMING, aiming ? Boolean.TRUE : null);
	}
}
