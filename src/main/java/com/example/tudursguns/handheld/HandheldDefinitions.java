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

	private static volatile Map<Identifier, AttachmentDefinition> serverAttachments = Map.of();
	private static volatile Map<Identifier, AttachmentDefinition> clientAttachments = Map.of();

	public static Map<Identifier, AttachmentDefinition> serverAttachments() {
		return serverAttachments;
	}

	static void setServerAttachments(Map<Identifier, AttachmentDefinition> loaded) {
		serverAttachments = Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
	}

	public static Map<Identifier, AttachmentDefinition> clientAttachments() {
		return clientAttachments;
	}

	public static void setClientAttachments(Map<Identifier, AttachmentDefinition> received) {
		clientAttachments = Collections.unmodifiableMap(new LinkedHashMap<>(received));
	}

	/** A weapon definition from whichever side has it - the server's (a server, or singleplayer),
	 * else the client's synced copy (a client connected to a dedicated server). For code that runs
	 * on both sides, such as slot checks, names and tooltips. */
	public static HandheldDefinition getAny(Identifier id) {
		if (id == null) {
			return null;
		}
		HandheldDefinition def = server.get(id);
		if (def != null) {
			return def;
		}
		ClientEntry entry = client.get(id);
		return entry == null ? null : entry.definition();
	}

	/** Same as getAny, for attachments. */
	public static AttachmentDefinition getAnyAttachment(Identifier id) {
		if (id == null) {
			return null;
		}
		AttachmentDefinition def = serverAttachments.get(id);
		return def != null ? def : clientAttachments.get(id);
	}

	/** A definition as the client sees it: the definition itself plus the weapon-file values the
	 * client needs for display (the client can't rely on reading weapon files itself - on a
	 * dedicated server they may exist only on the server). */
	public record ClientEntry(HandheldDefinition definition, WeaponSummary weapon) {
	}
}
