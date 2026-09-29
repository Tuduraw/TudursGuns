package com.example.tudursguns.mixin;

import com.example.tudursguns.entity.CosmeticProjectile;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A cosmetic projectile (a spent cartridge, a dropped magazine) isn't written to the world: it's
 * only there for a few seconds, and nothing would remove it again after a reload. */
@Mixin(Entity.class)
public abstract class EntityCosmeticSaveMixin {

	@Inject(method = "shouldSave()Z", at = @At("HEAD"), cancellable = true)
	private void tudursguns$skipCosmetic(CallbackInfoReturnable<Boolean> cir) {
		if (this instanceof CosmeticProjectile projectile && projectile.tudursguns$isCosmetic()) {
			cir.setReturnValue(false);
		}
	}
}
