package com.example.tudursguns.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

/** The local player's view kick from firing (RecoilPayload): spread over a couple of ticks so it
 * reads as a kick rather than a jump. The view stays where the kick left it - pulling it back down is
 * the shooter's job, as in most shooters. */
public final class RecoilController {

	private RecoilController() {
	}

	private static final int KICK_TICKS = 2;

	private static float pendingPitch;
	private static float pendingYaw;

	public static void kick(float pitch, float yaw) {
		pendingPitch += pitch;
		pendingYaw += yaw;
	}

	public static void reset() {
		pendingPitch = 0f;
		pendingYaw = 0f;
	}

	public static void tick(MinecraftClient client) {
		PlayerEntity player = client.player;
		if (player == null || (pendingPitch == 0f && pendingYaw == 0f)) {
			return;
		}
		float pitchStep = Math.abs(pendingPitch) <= 0.01f ? pendingPitch : pendingPitch / KICK_TICKS;
		float yawStep = Math.abs(pendingYaw) <= 0.01f ? pendingYaw : pendingYaw / KICK_TICKS;
		pendingPitch -= pitchStep;
		pendingYaw -= yawStep;
		player.setPitch(MathHelper.clamp(player.getPitch() - pitchStep, -90f, 90f));
		player.setYaw(player.getYaw() + yawStep);
	}
}
