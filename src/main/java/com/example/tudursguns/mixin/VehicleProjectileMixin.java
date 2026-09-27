package com.example.tudursguns.mixin;

import com.example.tudursguns.entity.CosmeticProjectile;
import com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A cosmetic projectile (see CosmeticProjectile) passes through every entity - blocks still stop
 * and bounce it. */
@Mixin(VehicleProjectileEntity.class)
public abstract class VehicleProjectileMixin implements CosmeticProjectile {

	@Unique
	private boolean tudursguns$cosmetic;

	@Override
	public void tudursguns$setCosmetic(boolean cosmetic) {
		this.tudursguns$cosmetic = cosmetic;
	}

	@Override
	public boolean tudursguns$isCosmetic() {
		return this.tudursguns$cosmetic;
	}

	@Inject(method = "canHit(Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
	private void tudursguns$hitNothing(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (this.tudursguns$cosmetic) {
			cir.setReturnValue(false);
		}
	}
}
