package com.example.tudursguns.entity;

/** Added to Tudur's Vehicle Mod's projectile (VehicleProjectileMixin): marks one spawned only to be
 * looked at - a spent cartridge or a dropped magazine (see FiringEffects). It hits no entity and
 * isn't saved with the world. */
public interface CosmeticProjectile {

	void tudursguns$setCosmetic(boolean cosmetic);

	boolean tudursguns$isCosmetic();
}
