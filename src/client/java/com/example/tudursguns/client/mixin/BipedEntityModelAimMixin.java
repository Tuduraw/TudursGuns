package com.example.tudursguns.client.mixin;

import com.example.tudursguns.client.render.AimRenderState;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player aiming a handheld weapon (see LivingEntityRendererAimMixin) holds it up with both hands,
 * using vanilla's loaded-crossbow pose on the main arm - two-handed, following the head's pitch.
 * A player sprinting with one carries it across the body instead: both arms forward and in, set
 * after vanilla's own arm swing (which they replace). */
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

	@Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V", at = @At("TAIL"))
	private void tudursguns$sprintCarry(BipedEntityRenderState state, CallbackInfo ci) {
		if (!Boolean.TRUE.equals(state.getData(AimRenderState.SPRINT_CARRY))) {
			return;
		}
		BipedEntityModel<?> model = (BipedEntityModel<?>) (Object) this;
		// Main arm low and turned in, the other arm reaching across to hold the fore-end.
		boolean left = state.mainArm == Arm.LEFT;
		ModelPart main = left ? model.leftArm : model.rightArm;
		ModelPart support = left ? model.rightArm : model.leftArm;
		float side = left ? -1f : 1f;
		main.pitch = -0.7f;
		main.yaw = -0.45f * side;
		main.roll = 0f;
		support.pitch = -1.0f;
		support.yaw = 0.75f * side;
		support.roll = 0f;
	}
}
