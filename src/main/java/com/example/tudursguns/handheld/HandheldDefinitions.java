package com.example.tudursguns.handheld;

import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Where the currently loaded HandheldDefinitions live.
 *
 * The server side and the client side are kept apart on purpose: in singleplayer both run in the
 * same JVM, and the client's copy is whatever the server last sent (see SyncHandheldDefinitionsPayload)
 * rather than a second read of the data files - the same set a dedicated server's players see. */
public final class HandheldDefinitions {

	private HandheldDefinitions() {
	}

	private static volatile Map<Identifier, HandheldDefinition> server = Map.of();
	private static volatile Map<Identifier, ClientEntry> client = Map.of();

	/** Server-side definitions, rebuilt on every data reload. */
	public static Map<Identifier, HandheldDefinition> server() {
		return server;
	}

	public static HandheldDefinition getServer(Identifier id) {
		return id == null ? null : server.get(id);
	}

	static void setServer(Map<Identifier, HandheldDefinition> loaded) {
		server = Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
	}

	/** Client-side definitions, as last received from the server. Empty until then. */
	public static Map<Identifier, ClientEntry> client() {
		return client;
	}

	public static ClientEntry getClient(Identifier id) {
		return id == null ? null : client.get(id);
	}

	public static void setClient(Map<Identifier, ClientEntry> received) {
		client = Collections.unmodifiableMap(new LinkedHashMap<>(received));
	}

	/** A definition as the client sees it: the definition itself plus the weapon-file values the
	 * client needs for display (the client can't rely on reading weapon files itself - on a
	 * dedicated server they may exist only on the server). */
	public record ClientEntry(HandheldDefinition definition, WeaponSummary weapon) {
	}
}
