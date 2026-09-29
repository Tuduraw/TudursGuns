package com.example.tudursguns.client.mixin;

import com.example.tudursguns.client.AimController;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No sprinting while aiming a handheld weapon: the player walks while the weapon is up. (A sprint
 * already running when aiming starts is ended by AimController.) */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntitySprintMixin {

	@Inject(method = "canStartSprinting()Z", at = @At("RETURN"), cancellable = true)
	private void tudursguns$noSprintWhileAiming(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && AimController.isAiming()) {
			cir.setReturnValue(false);
		}
	}
}
