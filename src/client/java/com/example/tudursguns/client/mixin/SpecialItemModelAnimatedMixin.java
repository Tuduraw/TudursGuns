package com.example.tudursguns.client.mixin;

import com.example.tudursguns.client.render.ObjDefinedItemRenderer;
import com.example.tudursguns.client.render.ObjHandheldModelRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.SpecialModelWrapper;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks this mod's OBJ items animated, so inventories draw them every frame instead of reusing the
 * first picture: addon textures load a tick or two after they're first drawn, and animated weapon
 * parts change anyway. */
@Mixin(SpecialModelWrapper.class)
public abstract class SpecialItemModelAnimatedMixin<T> {

	@Shadow
	@Final
	private SpecialModelRenderer<T> specialRenderer;

	@Inject(method = "update", at = @At("TAIL"))
	private void tudursguns$drawEveryFrame(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
			ItemDisplayContext displayContext, ClientLevel world, ItemOwner heldItemContext, int seed, CallbackInfo ci) {
		if (this.specialRenderer instanceof ObjHandheldModelRenderer || this.specialRenderer instanceof ObjDefinedItemRenderer) {
			state.setAnimated();
		}
	}
}
