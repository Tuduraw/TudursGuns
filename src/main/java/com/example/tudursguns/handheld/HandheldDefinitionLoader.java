package com.example.tudursguns.handheld;

import com.example.tudursguns.TudursGuns;
import com.example.tudursvehiclemod.asset.AddonPaths;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Scans data/<namespace>/handheld/*.json on every data reload (including /reload), plus the same
 * folder inside each pack under tudursvehiclemod-addons/ - the folder Tudur's Vehicle Mod already
 * reads vehicles and weapons from, so one addon pack can ship both. */
public class HandheldDefinitionLoader implements SimpleSynchronousResourceReloadListener {

	private static final String DIRECTORY = "handheld";
	private static final String SUFFIX = ".json";

	@Override
	public Identifier getFabricId() {
		return Identifier.of(TudursGuns.MOD_ID, "handheld_definitions");
	}

	@Override
	public void reload(ResourceManager manager) {
		Map<Identifier, HandheldDefinition> loaded = new LinkedHashMap<>();

		for (Map.Entry<Identifier, Resource> entry :
				manager.findResources(DIRECTORY, id -> id.getPath().endsWith(SUFFIX)).entrySet()) {
			Identifier fileId = entry.getKey();
			String path = fileId.getPath();
			Identifier id = Identifier.of(fileId.getNamespace(),
					path.substring(DIRECTORY.length() + 1, path.length() - SUFFIX.length()));
			try (Reader reader = entry.getValue().getReader()) {
				parseInto(loaded, id, reader, fileId.toString());
			} catch (Exception e) {
				TudursGuns.LOGGER.error("Failed to read handheld definition {}", fileId, e);
			}
		}

		int fromDataPacks = loaded.size();
		loadFromAddonsFolder(loaded);

		HandheldDefinitions.setServer(loaded);
		TudursGuns.LOGGER.info("Loaded {} handheld definition(s) ({} from tudursvehiclemod-addons/)",
				loaded.size(), loaded.size() - fromDataPacks);
	}

	private static void parseInto(Map<Identifier, HandheldDefinition> loaded, Identifier id, Reader reader, String source) {
		HandheldDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
				.resultOrPartial(error -> TudursGuns.LOGGER.error("Failed to parse handheld definition '{}' ({}): {}", id, source, error))
				.ifPresent(def -> loaded.put(id, def));
	}

	/** tudursvehiclemod-addons/<pack>/data/<namespace>/handheld/**.json - never throws. */
	private static void loadFromAddonsFolder(Map<Identifier, HandheldDefinition> loaded) {
		for (Path addonDir : AddonPaths.listSubdirectories(AddonPaths.getAddonsRoot())) {
			for (Path namespaceDir : AddonPaths.listSubdirectories(addonDir.resolve("data"))) {
				String namespace = namespaceDir.getFileName().toString();
				Path handheldDir = namespaceDir.resolve(DIRECTORY);
				if (!Files.isDirectory(handheldDir)) {
					continue;
				}
				try (var files = Files.walk(handheldDir)) {
					for (Path jsonFile : (Iterable<Path>) files.filter(p -> p.toString().endsWith(SUFFIX))::iterator) {
						String relative = handheldDir.relativize(jsonFile).toString().replace('\\', '/');
						Identifier id = Identifier.of(namespace, relative.substring(0, relative.length() - SUFFIX.length()));
						try (BufferedReader reader = Files.newBufferedReader(jsonFile, StandardCharsets.UTF_8)) {
							parseInto(loaded, id, reader, jsonFile.toString());
						} catch (Exception e) {
							TudursGuns.LOGGER.error("Failed to read handheld definition {}", jsonFile, e);
						}
					}
				} catch (Exception e) {
					TudursGuns.LOGGER.error("Failed to scan {}", handheldDir, e);
				}
			}
		}
	}
}
