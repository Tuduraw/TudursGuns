package com.example.tudursguns.client.mixin;

import com.example.tudursguns.client.AimController;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Scope zoom: narrows the world field of view by the scope's magnification while looking through
 * one. Exact for any magnification: tan(fov'/2) = tan(fov/2) / magnification. The hand/held-item FOV
 * (changingFov = false) is left alone. */
@Mixin(GameRenderer.class)
public abstract class GameRendererFovMixin {

	@Inject(method = "getFov(Lnet/minecraft/client/render/Camera;FZ)F", at = @At("RETURN"), cancellable = true)
	private void tudursguns$applyScopeZoom(Camera camera, float tickProgress, boolean changingFov, CallbackInfoReturnable<Float> cir) {
		if (!changingFov || !AimController.isScoped()) {
			return;
		}
		double halfFov = Math.toRadians(cir.getReturnValueF() / 2.0);
		double zoomed = 2.0 * Math.toDegrees(Math.atan(Math.tan(halfFov) / Math.max(1f, AimController.magnification())));
		cir.setReturnValue((float) zoomed);
	}
}
