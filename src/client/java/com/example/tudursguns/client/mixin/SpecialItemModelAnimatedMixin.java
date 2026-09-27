package com.example.tudursguns.client.mixin;

import com.example.tudursguns.client.render.ObjAttachmentModelRenderer;
import com.example.tudursguns.client.render.ObjDefinedItemRenderer;
import com.example.tudursguns.client.render.ObjHandheldModelRenderer;
import com.example.tudursguns.client.render.ObjThrowableModelRenderer;
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

/** Inventories draw each item once and reuse the picture for as long as its model key stays the
 * same. This mod's OBJ items can use textures from an addon folder, which Tudur's Vehicle Mod loads
 * only when first drawn (a tick or two later) - so the first, reused picture had no texture.
 * Marking them animated (as vanilla does for items whose look changes) has them drawn every frame
 * instead, so they pick the texture up as soon as it's loaded; animated weapon parts need it too. */
@Mixin(SpecialItemModel.class)
public abstract class SpecialItemModelAnimatedMixin<T> {

	@Shadow
	@Final
	private SpecialModelRenderer<T> specialModelType;

	@Inject(method = "update", at = @At("TAIL"))
	private void tudursguns$drawEveryFrame(ItemRenderState state, ItemStack stack, ItemModelManager resolver,
			ItemDisplayContext displayContext, ClientWorld world, HeldItemContext heldItemContext, int seed, CallbackInfo ci) {
		if (this.specialModelType instanceof ObjHandheldModelRenderer || this.specialModelType instanceof ObjDefinedItemRenderer
				|| this.specialModelType instanceof ObjAttachmentModelRenderer || this.specialModelType instanceof ObjThrowableModelRenderer) {
			state.markAnimated();
		}
	}
}
