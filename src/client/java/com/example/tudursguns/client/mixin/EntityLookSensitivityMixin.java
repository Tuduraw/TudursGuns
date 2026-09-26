package com.example.tudursguns.client.mixin;

import com.example.tudursguns.client.AimController;
import com.example.tudursguns.client.TudursGunsClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** While looking through a scope, the local player's mouse look is slowed in proportion to the
 * magnification, so the view moves across the screen at the usual speed (like the spyglass). */
@Mixin(Entity.class)
public abstract class EntityLookSensitivityMixin {

	@ModifyVariable(method = "changeLookDirection(DD)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double tudursguns$scaleYawDelta(double cursorDeltaX) {
		return tudursguns$scale(cursorDeltaX);
	}

	@ModifyVariable(method = "changeLookDirection(DD)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private double tudursguns$scalePitchDelta(double cursorDeltaY) {
		return tudursguns$scale(cursorDeltaY);
	}

	private double tudursguns$scale(double delta) {
		if (AimController.isScoped() && TudursGunsClientConfig.scaleSensitivityWithZoom()
				&& (Object) this == MinecraftClient.getInstance().player) {
			return delta / Math.max(1f, AimController.magnification());
		}
		return delta;
	}
}
