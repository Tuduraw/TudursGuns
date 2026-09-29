package com.example.tudursguns.client.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

	@Accessor("firstPersonRenderer")
	HeldItemRenderer tudursguns$getFirstPersonRenderer();
}
