package com.example.tudursguns.client;

import com.example.tudursguns.network.LockStatePayload;

/** The local player's missile lock-on state, as last reported by the server. */
public final class ClientLockState {

	private ClientLockState() {
	}

	private static volatile LockStatePayload current = LockStatePayload.NONE;

	public static LockStatePayload get() {
		return current;
	}

	public static void set(LockStatePayload state) {
		current = state;
	}

	public static boolean isTracking() {
		return current.targetId() >= 0;
	}

	public static boolean isLocked() {
		return isTracking() && current.progressTicks() >= current.requiredTicks();
	}
}
