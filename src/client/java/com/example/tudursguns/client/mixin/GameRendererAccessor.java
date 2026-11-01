package com.example.tudursguns.client.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

	@Accessor("itemInHandRenderer")
	ItemInHandRenderer tudursguns$getFirstPersonRenderer();
}
