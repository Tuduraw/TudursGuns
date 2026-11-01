package com.example.tudursguns.weapon;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Things to do for an entity some ticks from now (a sequence sound, a delayed cartridge). Dropped if
 * the entity is gone by then; not saved. */
public final class DelayedTasks {

	private DelayedTasks() {
	}

	private record Task(ResourceKey<Level> world, UUID entity, long due, Consumer<LivingEntity> run) {
	}

	private static final List<Task> TASKS = new ArrayList<>();

	/** Runs task after delayTicks (at once if 0 or less). */
	public static void schedule(LivingEntity entity, long delayTicks, Consumer<LivingEntity> task) {
		if (delayTicks <= 0) {
			task.accept(entity);
		} else {
			Level world = entity.level();
			TASKS.add(new Task(world.dimension(), entity.getUUID(), world.getGameTime() + delayTicks, task));
		}
	}

	/** Runs the tasks that are due. Called at the end of every server tick. */
	public static void tick(MinecraftServer server) {
		if (TASKS.isEmpty()) {
			return;
		}
		List<Runnable> due = new ArrayList<>();
		TASKS.removeIf(task -> {
			ServerLevel world = server.getLevel(task.world());
			Entity entity = world == null ? null : world.getEntity(task.entity());
			if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
				return true;
			}
			if (world.getGameTime() < task.due()) {
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
