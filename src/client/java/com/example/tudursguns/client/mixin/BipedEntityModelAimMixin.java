package com.example.tudursguns.client.mixin;

import com.example.tudursguns.client.render.AimRenderState;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player aiming a handheld weapon (see LivingEntityRendererAimMixin) holds it up with both hands,
 * using vanilla's loaded-crossbow pose on the main arm - two-handed, following the head's pitch. */
@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelAimMixin {

	@Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V", at = @At("HEAD"))
	private void tudursguns$raiseWeapon(BipedEntityRenderState state, CallbackInfo ci) {
		if (!Boolean.TRUE.equals(state.getData(AimRenderState.AIMING))) {
			return;
		}
		if (state.mainArm == Arm.LEFT) {
			state.leftArmPose = BipedEntityModel.ArmPose.CROSSBOW_HOLD;
		} else {
			state.rightArmPose = BipedEntityModel.ArmPose.CROSSBOW_HOLD;
		}
	}
}
