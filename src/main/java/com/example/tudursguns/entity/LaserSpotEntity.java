package com.example.tudursguns.entity;

import com.example.tudursguns.registry.ModEntityTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Where a laser designator is pointing: an invisible lock-on target (like the smoke decoy) plus a red
 * dot everyone can see. Its designator refreshes it every tick while in use; left alone for a few
 * ticks (the designator was put away) it removes itself. */
public class LaserSpotEntity extends SmokeDecoyEntity {

	private static final int TIMEOUT_TICKS = 5;

	private long lastRefreshed;

	public LaserSpotEntity(EntityType<? extends LivingEntity> type, Level world) {
		super(type, world);
	}

	public static LaserSpotEntity create(ServerLevel world, Vec3 pos) {
		LaserSpotEntity spot = new LaserSpotEntity(ModEntityTypes.LASER_SPOT, world);
		spot.setPos(pos.x, pos.y, pos.z);
		spot.lastRefreshed = world.getGameTime();
		world.addFreshEntity(spot);
		return spot;
	}

	/** Moves the spot and keeps it alive; shows the dot every other tick. */
	public void refresh(ServerLevel world, Vec3 pos) {
		this.setPos(pos.x, pos.y, pos.z);
		this.lastRefreshed = world.getGameTime();
		if (world.getGameTime() % 2 == 0) {
			world.sendParticles(new DustParticleOptions(0xFF1010, 1.2f), true, true, pos.x, pos.y, pos.z, 2, 0.03, 0.03, 0.03, 0.0);
		}
	}

	@Override
	protected boolean isOrphaned() {
		return this.level().getGameTime() - this.lastRefreshed > TIMEOUT_TICKS;
	}
}
