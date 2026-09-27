package com.example.tudursguns.weapon;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Things to do for a player some ticks from now (a sequence sound, a delayed cartridge). Dropped if
 * the player leaves; not saved. */
public final class PlayerTasks {

	private PlayerTasks() {
	}

	private record Task(UUID player, long due, Consumer<ServerPlayerEntity> run) {
	}

	private static final List<Task> TASKS = new ArrayList<>();

	/** Runs task after delayTicks (at once if 0 or less). */
	public static void schedule(ServerPlayerEntity player, long delayTicks, Consumer<ServerPlayerEntity> task) {
		if (delayTicks <= 0) {
			task.accept(player);
		} else {
			TASKS.add(new Task(player.getUuid(), player.getEntityWorld().getTime() + delayTicks, task));
		}
	}

	/** Runs the tasks that are due. Called at the end of every server tick. */
	public static void tick(MinecraftServer server) {
		if (TASKS.isEmpty()) {
			return;
		}
		List<Runnable> due = new ArrayList<>();
		TASKS.removeIf(task -> {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(task.player());
			if (player == null) {
				return true;
			}
			if (player.getEntityWorld().getTime() < task.due()) {
				return false;
			}
			due.add(() -> task.run().accept(player));
			return true;
		});
		due.forEach(Runnable::run);
	}

	/** Forgets everything (the server is stopping - a new world has its own clock). */
	public static void clear() {
		TASKS.clear();
	}
}
