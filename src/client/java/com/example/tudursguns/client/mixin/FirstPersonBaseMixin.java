package com.example.tudursguns.client.mixin;

import com.example.tudursguns.client.render.FirstPersonBase;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records the pose first-person hand rendering starts from - see FirstPersonBase. Same target as
 * Tudur's Vehicle Mod's own HeldItemRendererMixin. */
@Mixin(HeldItemRenderer.class)
public abstract class FirstPersonBaseMixin {

	@Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("HEAD"))
	private void tudursguns$captureBase(float tickProgress, MatrixStack matrices, OrderedRenderCommandQueue queue,
			ClientPlayerEntity player, int light, CallbackInfo ci) {
		FirstPersonBase.capture(matrices);
	}
}
