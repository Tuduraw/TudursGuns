package com.example.tudursguns.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's first-person arm drawing (the one used for holding a map with both hands) - it finds the
 * local player's skin and model itself. */
@Mixin(ItemInHandRenderer.class)
public interface HeldItemRendererInvoker {

	@Invoker("renderMapHand")
	void tudursguns$renderArm(PoseStack matrices, SubmitNodeCollector queue, int light, HumanoidArm arm);
}
