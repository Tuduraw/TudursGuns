package com.example.tudursguns.client.mixin;

import com.example.tudursguns.client.AimController;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While looking through a scope the mouse wheel changes magnification instead of the hotbar slot. */
@Mixin(Mouse.class)
public abstract class MouseScrollMixin {

	@Inject(method = "onMouseScroll(JDD)V", at = @At("HEAD"), cancellable = true)
	private void tudursguns$scopeMagnification(long window, double horizontal, double vertical, CallbackInfo ci) {
		if (AimController.isScoped()) {
			AimController.scroll(vertical);
			ci.cancel();
		}
	}
}
