package com.example.tudursguns.handheld;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.network.SyncDefinitionsPayload;
import com.example.tudursvehiclemod.asset.WeaponStatsLoader;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/** One kind of data-driven definition (data/<namespace>/<kind>/*.json): the server's copy, loaded
 * from data packs and the addons folder, and the client's copy, as last synced by the server.
 *
 * The two are kept apart because in singleplayer both sides share one JVM: the client's copy is
 * what the server sent - the same set a dedicated server's players see. Lookups that run on both
 * sides (names, tooltips, rendering) use getAny, which prefers the server's copy. */
public final class DefinitionSet<T> {

	private final String kind;
	private final Codec<T> codec;
	/** The name to show for a definition, worked out server-side (it may need a weapon file). */
	private final BiFunction<Identifier, T, String> displayName;
	/** The weapon file a definition names, or null for kinds without one. */
	private final Function<T, String> weaponFile;

	private volatile Map<Identifier, T> server = Map.of();
	private volatile Map<Identifier, T> client = Map.of();
	private volatile Map<Identifier, String> clientNames = Map.of();
	private volatile Map<Identifier, WeaponSummary> clientWeapons = Map.of();

	public DefinitionSet(String kind, Codec<T> codec, BiFunction<Identifier, T, String> displayName) {
		this(kind, codec, displayName, null);
	}

	public DefinitionSet(String kind, Codec<T> codec, BiFunction<Identifier, T, String> displayName, Function<T, String> weaponFile) {
		this.kind = kind;
		this.codec = codec;
		this.displayName = displayName;
		this.weaponFile = weaponFile;
	}

	public String kind() {
		return this.kind;
	}

	public DefinitionLoader<T> loader() {
		return new DefinitionLoader<>(this.kind, this.codec,
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
		return def != null ? this.displayName.apply(id, def) : this.clientNames.get(id);
	}

	/** The definition's weapon-file values - read live on the server, as synced on a client. Null if
	 * the definition isn't known on this side or its kind names no weapon file. */
	public WeaponSummary weapon(Identifier id) {
		if (id == null || this.weaponFile == null) {
			return null;
		}
		T def = this.server.get(id);
		return def != null ? summary(def) : this.clientWeapons.get(id);
	}

	private WeaponSummary summary(T def) {
		return WeaponSummary.of(WeaponStatsLoader.get(this.weaponFile.apply(def)));
	}

	public SyncDefinitionsPayload buildPayload() {
		List<SyncDefinitionsPayload.Entry> entries = new ArrayList<>();
		for (Map.Entry<Identifier, T> entry : this.server.entrySet()) {
			DataResult<JsonElement> json = this.codec.encodeStart(JsonOps.INSTANCE, entry.getValue());
			if (json.result().isEmpty()) {
				// A value the codec reads but won't write back - logged rather than silently left out.
				TudursGuns.LOGGER.error("Failed to encode {} definition '{}' for clients: {}", this.kind, entry.getKey(),
						json.error().map(DataResult.Error::message).orElse("?"));
				continue;
			}
			entries.add(new SyncDefinitionsPayload.Entry(entry.getKey(), json.result().get().toString(),
					this.displayName.apply(entry.getKey(), entry.getValue()),
					this.weaponFile == null ? Optional.empty() : Optional.of(summary(entry.getValue()))));
		}
		return new SyncDefinitionsPayload(this.kind, entries);
	}

	/** Client side: replaces the client copy with what the server sent. */
	public void receive(SyncDefinitionsPayload payload) {
		Map<Identifier, T> received = new LinkedHashMap<>();
		Map<Identifier, String> names = new LinkedHashMap<>();
		Map<Identifier, WeaponSummary> weapons = new LinkedHashMap<>();
		for (SyncDefinitionsPayload.Entry entry : payload.entries()) {
			try {
				this.codec.parse(JsonOps.INSTANCE, JsonParser.parseString(entry.definitionJson()))
						.resultOrPartial(error -> TudursGuns.LOGGER.error("Failed to decode {} definition '{}': {}", this.kind, entry.id(), error))
						.ifPresent(def -> {
							received.put(entry.id(), def);
							names.put(entry.id(), entry.displayName());
							entry.weapon().ifPresent(weapon -> weapons.put(entry.id(), weapon));
						});
			} catch (Exception e) {
				TudursGuns.LOGGER.error("Failed to decode {} definition '{}'", this.kind, entry.id(), e);
			}
		}
		this.client = Collections.unmodifiableMap(received);
		this.clientNames = Collections.unmodifiableMap(names);
		this.clientWeapons = Collections.unmodifiableMap(weapons);
	}

	public void clearClient() {
		this.client = Map.of();
		this.clientNames = Map.of();
		this.clientWeapons = Map.of();
	}
}
