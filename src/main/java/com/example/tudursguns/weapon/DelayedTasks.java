package com.example.tudursguns.weapon;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Things to do for an entity some ticks from now (a sequence sound, a delayed cartridge). Dropped if
 * the entity is gone by then; not saved. */
public final class DelayedTasks {

	private DelayedTasks() {
	}

	private record Task(RegistryKey<World> world, UUID entity, long due, Consumer<LivingEntity> run) {
	}

	private static final List<Task> TASKS = new ArrayList<>();

	/** Runs task after delayTicks (at once if 0 or less). */
	public static void schedule(LivingEntity entity, long delayTicks, Consumer<LivingEntity> task) {
		if (delayTicks <= 0) {
			task.accept(entity);
		} else {
			World world = entity.getEntityWorld();
			TASKS.add(new Task(world.getRegistryKey(), entity.getUuid(), world.getTime() + delayTicks, task));
		}
	}

	/** Runs the tasks that are due. Called at the end of every server tick. */
	public static void tick(MinecraftServer server) {
		if (TASKS.isEmpty()) {
			return;
		}
		List<Runnable> due = new ArrayList<>();
		TASKS.removeIf(task -> {
			ServerWorld world = server.getWorld(task.world());
			Entity entity = world == null ? null : world.getEntity(task.entity());
			if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
				return true;
			}
			if (world.getTime() < task.due()) {
				return false;
			}
			due.add(() -> task.run().accept(living));
			return true;
		});
		due.forEach(Runnable::run);
	}

	/** Forgets everything (the server is stopping - a new world has its own clock). */
	public static void clear() {
		TASKS.clear();
	}
}
