package com.example.tudursguns.client.mixin;

import com.example.tudursguns.client.render.ObjDefinedItemRenderer;
import com.example.tudursguns.client.render.ObjHandheldModelRenderer;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.model.SpecialItemModel;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.HeldItemContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks this mod's OBJ items animated, so inventories draw them every frame instead of reusing the
 * first picture: addon textures load a tick or two after they're first drawn, and animated weapon
 * parts change anyway. */
@Mixin(SpecialItemModel.class)
public abstract class SpecialItemModelAnimatedMixin<T> {

	@Shadow
	@Final
	private SpecialModelRenderer<T> specialModelType;

	@Inject(method = "update", at = @At("TAIL"))
	private void tudursguns$drawEveryFrame(ItemRenderState state, ItemStack stack, ItemModelManager resolver,
			ItemDisplayContext displayContext, ClientWorld world, HeldItemContext heldItemContext, int seed, CallbackInfo ci) {
		if (this.specialModelType instanceof ObjHandheldModelRenderer || this.specialModelType instanceof ObjDefinedItemRenderer) {
			state.markAnimated();
		}
	}
}
