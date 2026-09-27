package com.example.tudursguns.handheld;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.network.SyncDefinitionsPayload;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/** One kind of data-driven definition (data/<namespace>/<kind>/*.json): its server-side copy (loaded
 * from data packs and the addons folder), and its client-side copy (as last synced by the server).
 * Kept apart for the same reason as HandheldDefinitions: in singleplayer both sides share one JVM. */
public final class DefinitionSet<T> {

	private final String kind;
	private final Codec<T> codec;
	/** The name to show for a definition, worked out server-side (it may need a weapon file). */
	private final BiFunction<Identifier, T, String> displayName;

	private volatile Map<Identifier, T> server = Map.of();
	private volatile Map<Identifier, T> client = Map.of();
	private volatile Map<Identifier, String> clientNames = Map.of();

	public DefinitionSet(String kind, Codec<T> codec, BiFunction<Identifier, T, String> displayName) {
		this.kind = kind;
		this.codec = codec;
		this.displayName = displayName;
	}

	public String kind() {
		return this.kind;
	}

	public HandheldDefinitionLoader<T> loader() {
		return new HandheldDefinitionLoader<>(this.kind, this.codec,
				loaded -> this.server = Collections.unmodifiableMap(new LinkedHashMap<>(loaded)));
	}

	public Map<Identifier, T> server() {
		return this.server;
	}

	public T getServer(Identifier id) {
		return id == null ? null : this.server.get(id);
	}

	public Map<Identifier, T> client() {
		return this.client;
	}

	/** From whichever side has it - the server's first, else the client's synced copy. */
	public T getAny(Identifier id) {
		if (id == null) {
			return null;
		}
		T def = this.server.get(id);
		return def != null ? def : this.client.get(id);
	}

	/** Display name, or null if the definition isn't known on this side. */
	public String name(Identifier id) {
		if (id == null) {
			return null;
		}
		T def = this.server.get(id);
		if (def != null) {
			return this.displayName.apply(id, def);
		}
		return this.clientNames.get(id);
	}

	public SyncDefinitionsPayload buildPayload() {
		List<SyncDefinitionsPayload.Entry> entries = new ArrayList<>();
		for (Map.Entry<Identifier, T> entry : this.server.entrySet()) {
			this.codec.encodeStart(JsonOps.INSTANCE, entry.getValue()).result().ifPresent(json ->
					entries.add(new SyncDefinitionsPayload.Entry(entry.getKey(), json.toString(),
							this.displayName.apply(entry.getKey(), entry.getValue()))));
		}
		return new SyncDefinitionsPayload(this.kind, entries);
	}

	/** Client side: replaces the client copy with what the server sent. */
	public void receive(SyncDefinitionsPayload payload) {
		Map<Identifier, T> received = new LinkedHashMap<>();
		Map<Identifier, String> names = new LinkedHashMap<>();
		for (SyncDefinitionsPayload.Entry entry : payload.entries()) {
			try {
				this.codec.parse(JsonOps.INSTANCE, JsonParser.parseString(entry.definitionJson()))
						.resultOrPartial(error -> TudursGuns.LOGGER.error("Failed to decode {} definition '{}': {}", this.kind, entry.id(), error))
						.ifPresent(def -> {
							received.put(entry.id(), def);
							names.put(entry.id(), entry.displayName());
						});
			} catch (Exception e) {
				TudursGuns.LOGGER.error("Failed to decode {} definition '{}'", this.kind, entry.id(), e);
			}
		}
		this.client = Collections.unmodifiableMap(received);
		this.clientNames = Collections.unmodifiableMap(names);
	}

	public void clearClient() {
		this.client = Map.of();
		this.clientNames = Map.of();
	}
}
