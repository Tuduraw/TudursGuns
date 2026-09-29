package com.example.tudursguns.entity;

import com.example.tudursguns.registry.ModEntityTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Where a laser designator is pointing: an invisible lock-on target (like the smoke decoy) plus a red
 * dot everyone can see. Its designator refreshes it every tick while in use; left alone for a few
 * ticks (the designator was put away) it removes itself. */
public class LaserSpotEntity extends SmokeDecoyEntity {

	private static final int TIMEOUT_TICKS = 5;

	private long lastRefreshed;

	public LaserSpotEntity(EntityType<? extends LivingEntity> type, World world) {
		super(type, world);
	}

	public static LaserSpotEntity create(ServerWorld world, Vec3d pos) {
		LaserSpotEntity spot = new LaserSpotEntity(ModEntityTypes.LASER_SPOT, world);
		spot.setPosition(pos.x, pos.y, pos.z);
		spot.lastRefreshed = world.getTime();
		world.spawnEntity(spot);
		return spot;
	}

	/** Moves the spot and keeps it alive; shows the dot every other tick. */
	public void refresh(ServerWorld world, Vec3d pos) {
		this.setPosition(pos.x, pos.y, pos.z);
		this.lastRefreshed = world.getTime();
		if (world.getTime() % 2 == 0) {
			world.spawnParticles(new DustParticleEffect(0xFF1010, 1.2f), true, true, pos.x, pos.y, pos.z, 2, 0.03, 0.03, 0.03, 0.0);
		}
	}

	@Override
	protected boolean isOrphaned() {
		return this.getEntityWorld().getTime() - this.lastRefreshed > TIMEOUT_TICKS;
	}
}
