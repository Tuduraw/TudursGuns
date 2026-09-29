package com.example.tudursguns.mixin;

import com.example.tudursguns.armor.ArmorEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Armor effects that hook vanilla's own calculations (see ArmorEffects):
 * - modifyAppliedDamage: the last step of damage reduction (after armor and enchantments) - headshots
 *   and ballistic/blast protection multiply on top. Tudur's Vehicle Mod's passenger damage factor is
 *   applied earlier (at the start of damage()), so the two simply combine.
 * - getAttackDistanceScalingFactor: how far away mobs notice this entity (vanilla uses it for mob
 *   heads and invisibility) - camouflage scales it. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityArmorMixin {

	@Inject(method = "modifyAppliedDamage(Lnet/minecraft/entity/damage/DamageSource;F)F", at = @At("RETURN"), cancellable = true)
	private void tudursguns$armorProtection(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
		float modified = ArmorEffects.modifyDamage((LivingEntity) (Object) this, source, cir.getReturnValueF());
		if (modified != cir.getReturnValueF()) {
			cir.setReturnValue(modified);
		}
	}

	@Inject(method = "getAttackDistanceScalingFactor(Lnet/minecraft/entity/Entity;)D", at = @At("RETURN"), cancellable = true)
	private void tudursguns$camouflage(Entity entity, CallbackInfoReturnable<Double> cir) {
		double multiplier = ArmorEffects.detectionMultiplier((LivingEntity) (Object) this);
		if (multiplier != 1.0) {
			cir.setReturnValue(cir.getReturnValueD() * multiplier);
		}
	}
}
