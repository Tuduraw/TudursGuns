package com.example.tudursguns.mixin;

import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Tudur's Vehicle Mod's own per-second supply steps (the ones its supply vehicles apply) are
 * private; the ammo box reuses them so it rearms vehicles by exactly the same rules. remap = false:
 * these are the vehicle mod's own methods, not Minecraft's. */
@Mixin(value = AbstractVehicleEntity.class, remap = false)
public interface AbstractVehicleEntityAccessor {

	/** One supply step: tops up the magazine by 10% of its size, or - once full - the reserve by 10%
	 * of MaxAmmo, never beyond the weapon's own capacity. */
	@Invoker("tudursvehiclemod$receiveAmmoSupply")
	void tudursguns$receiveAmmoSupply();

	/** One supply step: restores 2% of max health (capped at max). */
	@Invoker("tudursvehiclemod$receiveHealthSupply")
	void tudursguns$receiveHealthSupply();
}
