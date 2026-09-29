package com.example.tudursguns.client.mixin;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's first-person arm drawing (the one used for holding a map with both hands) - it finds the
 * local player's skin and model itself. */
@Mixin(HeldItemRenderer.class)
public interface HeldItemRendererInvoker {

	@Invoker("renderArm")
	void tudursguns$renderArm(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, Arm arm);
}
