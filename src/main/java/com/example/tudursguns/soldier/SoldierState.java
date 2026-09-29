package com.example.tudursguns.soldier;

import com.example.tudursguns.TudursGunsConfig;

/** What a soldier post's soldier is doing, as its post sees it - shown on the post's screen, and
 * deciding how fast the post uses food (the server config's soldier_food_* rates). */
public enum SoldierState {
	/** No soldier out: the post is stood down, or has no weapon or food. */
	STANDBY,
	/** Killed; the post sends another after soldier_respawn_ticks. */
	DOWN,
	/** Out, but somewhere not loaded. */
	AWAY,
	/** At the post, with no route to walk. */
	IDLE,
	PATROL,
	COMBAT,
	/** Walking back to the post: stood down, or out of food. */
	RETURNING,
	/** Magazine empty: waiting at the post for an ammo box to refill it. */
	OUT_OF_AMMO;

	/** Food used per tick in this state. */
	public float foodPerTick() {
		TudursGunsConfig.Data config = TudursGunsConfig.get();
		float perMinute = switch (this) {
			case STANDBY, DOWN -> 0f;
			case AWAY, IDLE, OUT_OF_AMMO -> config.soldier_food_idle;
			case PATROL, RETURNING -> config.soldier_food_patrol;
			case COMBAT -> config.soldier_food_combat;
		};
		return perMinute / 1200f;
	}
}
