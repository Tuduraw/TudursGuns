package com.example.tudursguns.soldier;

/** A mob that fights with this mod's weapons and armor - a soldier posted at a soldier post, or an
 * enemy soldier. Its weapon is in the main hand, its armor in the armor slots; SoldierWeaponGoal
 * does the shooting. */
public interface Soldier {

	/** Whether it's holding its weapon up (drawn in the raised two-handed pose). */
	boolean isAimingWeapon();

	void setAimingWeapon(boolean aiming);

	/** Enemy soldiers carry endless rounds and reload by time; a post's soldier is refilled by ammo
	 * boxes and can't fire once its magazine is empty. */
	boolean hasUnlimitedAmmo();

	/** Whether it may fire now (a post's soldier needs food). */
	boolean mayFire();

	/** Distance (blocks) it engages targets at. */
	double engageRange();
}
