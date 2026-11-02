package com.example.tudursguns.network;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.handheld.WeaponSummary;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** S2C: every definition of one kind (see ModDefinitions) as JSON, with the display name the server
 * worked out for it and, for kinds that name a weapon file, that file's summary - both may come from
 * a weapon file the client can't read. */
public record SyncDefinitionsPayload(String kind, List<Entry> entries) implements CustomPacketPayload {

	public record Entry(Identifier id, String definitionJson, String displayName, Optional<WeaponSummary> weapon) {

		static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
				Identifier.STREAM_CODEC, Entry::id,
				ByteBufCodecs.STRING_UTF8, Entry::definitionJson,
				ByteBufCodecs.STRING_UTF8, Entry::displayName,
				ByteBufCodecs.optional(WeaponSummary.PACKET_CODEC), Entry::weapon,
				Entry::new);
	}

	public static final CustomPacketPayload.Type<SyncDefinitionsPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, "sync_definitions"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SyncDefinitionsPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, SyncDefinitionsPayload::kind,
			Entry.CODEC.apply(ByteBufCodecs.list()), SyncDefinitionsPayload::entries,
			SyncDefinitionsPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
