package com.example.tudursguns.network;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.handheld.WeaponSummary;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;

/** S2C: every definition of one kind (see ModDefinitions) as JSON, with the display name the server
 * worked out for it and, for kinds that name a weapon file, that file's summary - both may come from
 * a weapon file the client can't read. */
public record SyncDefinitionsPayload(String kind, List<Entry> entries) implements CustomPayload {

	public record Entry(Identifier id, String definitionJson, String displayName, Optional<WeaponSummary> weapon) {

		static final PacketCodec<RegistryByteBuf, Entry> CODEC = PacketCodec.tuple(
				Identifier.PACKET_CODEC, Entry::id,
				PacketCodecs.STRING, Entry::definitionJson,
				PacketCodecs.STRING, Entry::displayName,
				PacketCodecs.optional(WeaponSummary.PACKET_CODEC), Entry::weapon,
				Entry::new);
	}

	public static final CustomPayload.Id<SyncDefinitionsPayload> ID =
			new CustomPayload.Id<>(Identifier.of(TudursGuns.MOD_ID, "sync_definitions"));

	public static final PacketCodec<RegistryByteBuf, SyncDefinitionsPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.STRING, SyncDefinitionsPayload::kind,
			Entry.CODEC.collect(PacketCodecs.toList()), SyncDefinitionsPayload::entries,
			SyncDefinitionsPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
