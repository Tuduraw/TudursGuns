package com.example.tudursguns.client.mixin;

import com.example.tudursguns.client.render.AimRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player aiming a handheld weapon (see LivingEntityRendererAimMixin) holds it up with both hands,
 * using vanilla's loaded-crossbow pose on the main arm - two-handed, following the head's pitch.
 * A player sprinting with one carries it across the body instead: both arms forward and in, set
 * after vanilla's own arm swing (which they replace). */
@Mixin(HumanoidModel.class)
public abstract class BipedEntityModelAimMixin {

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("HEAD"))
	private void tudursguns$raiseWeapon(HumanoidRenderState state, CallbackInfo ci) {
		if (!Boolean.TRUE.equals(state.getData(AimRenderState.AIMING))) {
			return;
		}
		if (state.mainArm == HumanoidArm.LEFT) {
			state.leftArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
		} else {
			state.rightArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
		}
	}

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void tudursguns$sprintCarry(HumanoidRenderState state, CallbackInfo ci) {
		if (!Boolean.TRUE.equals(state.getData(AimRenderState.SPRINT_CARRY))) {
			return;
		}
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		// Main arm low and turned in, the other arm reaching across to hold the fore-end.
		boolean left = state.mainArm == HumanoidArm.LEFT;
		ModelPart main = left ? model.leftArm : model.rightArm;
		ModelPart support = left ? model.rightArm : model.leftArm;
		float side = left ? -1f : 1f;
		main.xRot = -0.7f;
		main.yRot = -0.45f * side;
		main.zRot = 0f;
		support.xRot = -1.0f;
		support.yRot = 0.75f * side;
		support.zRot = 0f;
	}
}
