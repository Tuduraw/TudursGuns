package com.example.tudursguns.client;

import com.example.tudursguns.TudursGuns;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.joml.Vector3f;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Client settings, stored in config/tudursguns-client.json (created with defaults on first run).
 *
 * The offsets apply to every weapon on top of its own definition - for adjusting the first-person
 * view to taste (or to a resource pack's models) without editing the definitions. All are in blocks.
 * aim_offset and hip_offset are camera-space (x right, y up, z towards the viewer); the arm offsets
 * are in the weapon's model space, like the definitions' own aim.right_arm/left_arm. */
public final class TudursGunsClientConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("tudursguns-client.json");

	private static Data data = new Data();

	private TudursGunsClientConfig() {
	}

	/** The JSON layout. Arrays are [x, y, z]. */
	public static final class Data {
		float[] aim_offset = {0f, 0f, 0f};
		float[] hip_offset = {0f, 0f, 0f};
		float[] right_arm_offset = {0f, 0f, 0f};
		float[] left_arm_offset = {0f, 0f, 0f};
		/** Draw the player's arms holding the weapon in first person (weapons whose definition places them). */
		boolean show_arms = true;
		/** Slow the mouse down in proportion to the scope's magnification. */
		boolean scale_sensitivity_with_zoom = true;
		/** Last magnification used per scope attachment id - also where to set a starting value. */
		Map<String, Float> scope_magnification = new LinkedHashMap<>();
	}

	public static void load() {
		try {
			if (Files.exists(PATH)) {
				try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
					Data loaded = GSON.fromJson(reader, Data.class);
					if (loaded != null) {
						data = sanitize(loaded);
					}
				}
			}
		} catch (Exception e) {
			TudursGuns.LOGGER.error("Failed to read {} - using defaults", PATH, e);
			data = new Data();
		}
		save();
	}

	private static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(data, writer);
			}
		} catch (Exception e) {
			TudursGuns.LOGGER.error("Failed to write {}", PATH, e);
		}
	}

	/** Fills in anything missing from an older or hand-edited file. */
	private static Data sanitize(Data loaded) {
		Data defaults = new Data();
		loaded.aim_offset = vector(loaded.aim_offset, defaults.aim_offset);
		loaded.hip_offset = vector(loaded.hip_offset, defaults.hip_offset);
		loaded.right_arm_offset = vector(loaded.right_arm_offset, defaults.right_arm_offset);
		loaded.left_arm_offset = vector(loaded.left_arm_offset, defaults.left_arm_offset);
		if (loaded.scope_magnification == null) {
			loaded.scope_magnification = new LinkedHashMap<>();
		}
		return loaded;
	}

	private static float[] vector(float[] value, float[] fallback) {
		return value != null && value.length == 3 ? value : fallback;
	}

	public static Vector3f aimOffset() {
		return new Vector3f(data.aim_offset[0], data.aim_offset[1], data.aim_offset[2]);
	}

	public static Vector3f hipOffset() {
		return new Vector3f(data.hip_offset[0], data.hip_offset[1], data.hip_offset[2]);
	}

	public static Vector3f rightArmOffset() {
		return new Vector3f(data.right_arm_offset[0], data.right_arm_offset[1], data.right_arm_offset[2]);
	}

	public static Vector3f leftArmOffset() {
		return new Vector3f(data.left_arm_offset[0], data.left_arm_offset[1], data.left_arm_offset[2]);
	}

	public static boolean showArms() {
		return data.show_arms;
	}

	public static boolean scaleSensitivityWithZoom() {
		return data.scale_sensitivity_with_zoom;
	}

	public static Float scopeMagnification(String scopeId) {
		return data.scope_magnification.get(scopeId);
	}

	public static void setScopeMagnification(String scopeId, float magnification) {
		Float previous = data.scope_magnification.put(scopeId, magnification);
		if (previous == null || previous != magnification) {
			save();
		}
	}
}
