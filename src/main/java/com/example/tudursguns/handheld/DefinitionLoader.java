package com.example.tudursguns.handheld;

import com.example.tudursguns.TudursGuns;
import com.example.tudursvehiclemod.asset.AddonPaths;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
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
import java.util.function.Consumer;

/** Reads one kind of definition (see DefinitionSet) from data/<namespace>/<directory>/*.json on every
 * data reload (including /reload), plus the same folder inside each pack under
 * tudursvehiclemod-addons/ - where Tudur's Vehicle Mod reads vehicles and weapons from, so one addon
 * pack can ship both. */
public class DefinitionLoader<T> implements SimpleSynchronousResourceReloadListener {

	private static final String SUFFIX = ".json";

	private final String directory;
	private final Codec<T> codec;
	private final Consumer<Map<Identifier, T>> sink;

	DefinitionLoader(String directory, Codec<T> codec, Consumer<Map<Identifier, T>> sink) {
		this.directory = directory;
		this.codec = codec;
		this.sink = sink;
	}

	@Override
	public Identifier getFabricId() {
		return Identifier.of(TudursGuns.MOD_ID, this.directory + "_definitions");
	}

	@Override
	public void reload(ResourceManager manager) {
		Map<Identifier, T> loaded = new LinkedHashMap<>();

		for (Map.Entry<Identifier, Resource> entry :
				manager.findResources(this.directory, id -> id.getPath().endsWith(SUFFIX)).entrySet()) {
			Identifier fileId = entry.getKey();
			String path = fileId.getPath();
			Identifier id = Identifier.of(fileId.getNamespace(),
					path.substring(this.directory.length() + 1, path.length() - SUFFIX.length()));
			try (Reader reader = entry.getValue().getReader()) {
				parseInto(loaded, id, reader, fileId.toString());
			} catch (Exception e) {
				TudursGuns.LOGGER.error("Failed to read {} definition {}", this.directory, fileId, e);
			}
		}

		int fromDataPacks = loaded.size();
		loadFromAddonsFolder(loaded);

		this.sink.accept(loaded);
		TudursGuns.LOGGER.info("Loaded {} {} definition(s) ({} from tudursvehiclemod-addons/)",
				loaded.size(), this.directory, loaded.size() - fromDataPacks);
	}

	private void parseInto(Map<Identifier, T> loaded, Identifier id, Reader reader, String source) {
		this.codec.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
				.resultOrPartial(error -> TudursGuns.LOGGER.error("Failed to parse {} definition '{}' ({}): {}", this.directory, id, source, error))
				.ifPresent(def -> loaded.put(id, def));
	}

	/** tudursvehiclemod-addons/<pack>/data/<namespace>/<directory>/**.json - never throws. */
	private void loadFromAddonsFolder(Map<Identifier, T> loaded) {
		for (Path addonDir : AddonPaths.listSubdirectories(AddonPaths.getAddonsRoot())) {
			for (Path namespaceDir : AddonPaths.listSubdirectories(addonDir.resolve("data"))) {
				String namespace = namespaceDir.getFileName().toString();
				Path kindDir = namespaceDir.resolve(this.directory);
				if (!Files.isDirectory(kindDir)) {
					continue;
				}
				try (var files = Files.walk(kindDir)) {
					for (Path jsonFile : (Iterable<Path>) files.filter(p -> p.toString().endsWith(SUFFIX))::iterator) {
						String relative = kindDir.relativize(jsonFile).toString().replace('\\', '/');
						Identifier id = Identifier.of(namespace, relative.substring(0, relative.length() - SUFFIX.length()));
						try (BufferedReader reader = Files.newBufferedReader(jsonFile, StandardCharsets.UTF_8)) {
							parseInto(loaded, id, reader, jsonFile.toString());
						} catch (Exception e) {
							TudursGuns.LOGGER.error("Failed to read {} definition {}", this.directory, jsonFile, e);
						}
					}
				} catch (Exception e) {
					TudursGuns.LOGGER.error("Failed to scan {}", kindDir, e);
				}
			}
		}
	}
}
