package com.example.tudursguns;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Server settings, stored in config/tudursguns-server.json (created with defaults on first run,
 * read at startup). */
public final class TudursGunsConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("tudursguns-server.json");

	private static Data data = new Data();

	private TudursGunsConfig() {
	}

	public static final class Data {
		/** Damage multiplier for a projectile hitting a creature's head (1 = no headshots). A helmet's
		 * headshot protection takes off part of the extra. */
		float headshot_multiplier = 1.5f;
		/** Headshots only for players (true), or for every creature (false). */
		boolean headshots_players_only = false;

		/** Enemy soldiers spawn naturally (in the dark, where monsters do). */
		public boolean enemy_soldier_natural_spawn = true;
		/** Spawn weight among monsters (a zombie is 100). */
		public int enemy_soldier_spawn_weight = 15;
		/** Group size of a natural spawn. */
		public int enemy_soldier_group_min = 1;
		public int enemy_soldier_group_max = 3;
		/** Weight of "nothing" when an enemy soldier's armor is picked, per slot (against the armor's
		 * own soldier_weight values). */
		public int enemy_soldier_no_armor_weight = 40;
		/** Chance each piece of an enemy soldier's gear drops when it's killed by a player. */
		public float enemy_soldier_drop_chance = 0.05f;
		/** Distance enemy soldiers engage at. */
		public float enemy_soldier_engage_range = 32f;
		/** Enemy soldiers despawn like other monsters (false: they stay, like named mobs). */
		public boolean enemy_soldier_despawn = false;
		/** No natural spawn where this many enemy soldiers are already within enemy_soldier_density_radius. */
		public int enemy_soldier_density_max = 4;
		public int enemy_soldier_density_radius = 64;
		/** Monsters that hunt players hunt posted soldiers too. */
		public boolean monsters_target_soldiers = true;

		/** Food (hunger points) a post's soldier eats per minute: waiting, patrolling, fighting. */
		public float soldier_food_idle = 1f;
		public float soldier_food_patrol = 2f;
		public float soldier_food_combat = 4f;
		/** Ticks before a post sends a new soldier after one is killed, and the food that costs. */
		public int soldier_respawn_ticks = 600;
		public float soldier_respawn_food = 10f;
	}

	public static void load() {
		try {
			if (Files.exists(PATH)) {
				try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
					Data loaded = GSON.fromJson(reader, Data.class);
					if (loaded != null) {
						data = loaded;
					}
				}
			}
			data.headshot_multiplier = Math.max(1f, Math.min(10f, data.headshot_multiplier));
			data.enemy_soldier_spawn_weight = Math.max(0, data.enemy_soldier_spawn_weight);
			data.enemy_soldier_group_min = Math.max(1, data.enemy_soldier_group_min);
			data.enemy_soldier_group_max = Math.max(data.enemy_soldier_group_min, data.enemy_soldier_group_max);
			data.enemy_soldier_no_armor_weight = Math.max(0, data.enemy_soldier_no_armor_weight);
			data.enemy_soldier_drop_chance = Math.max(0f, Math.min(1f, data.enemy_soldier_drop_chance));
			data.enemy_soldier_engage_range = Math.max(4f, Math.min(128f, data.enemy_soldier_engage_range));
			data.enemy_soldier_density_max = Math.max(0, data.enemy_soldier_density_max);
			data.enemy_soldier_density_radius = Math.max(8, Math.min(256, data.enemy_soldier_density_radius));
			data.soldier_food_idle = Math.max(0f, data.soldier_food_idle);
			data.soldier_food_patrol = Math.max(0f, data.soldier_food_patrol);
			data.soldier_food_combat = Math.max(0f, data.soldier_food_combat);
			data.soldier_respawn_ticks = Math.max(0, data.soldier_respawn_ticks);
			data.soldier_respawn_food = Math.max(0f, data.soldier_respawn_food);
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(data, writer);
			}
		} catch (Exception e) {
			TudursGuns.LOGGER.error("Failed to read/write {} - using defaults", PATH, e);
		}
	}

	public static float headshotMultiplier() {
		return data.headshot_multiplier;
	}

	public static boolean headshotsPlayersOnly() {
		return data.headshots_players_only;
	}

	public static Data get() {
		return data;
	}
}
