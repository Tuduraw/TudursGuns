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
}
