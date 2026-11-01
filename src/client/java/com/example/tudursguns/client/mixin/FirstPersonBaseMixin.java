package com.example.tudursguns.client.mixin;

import com.example.tudursguns.client.render.FirstPersonBase;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records the pose first-person hand rendering starts from - see FirstPersonBase. Same target as
 * Tudur's Vehicle Mod's own HeldItemRendererMixin. */
@Mixin(ItemInHandRenderer.class)
public abstract class FirstPersonBaseMixin {

	@Inject(method = "renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V", at = @At("HEAD"))
	private void tudursguns$captureBase(float tickProgress, PoseStack matrices, SubmitNodeCollector queue,
			LocalPlayer player, int light, CallbackInfo ci) {
		FirstPersonBase.capture(matrices);
	}
}
