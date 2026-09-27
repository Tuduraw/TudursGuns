package com.example.tudursguns.network;

import com.example.tudursguns.TudursGuns;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** S2C: every definition of one kind (mine, armor, equipment - see ModDefinitions) as JSON, plus the
 * display name the server worked out for it (which may come from a weapon file the client can't read). */
public record SyncDefinitionsPayload(String kind, List<Entry> entries) implements CustomPayload {

	public record Entry(Identifier id, String definitionJson, String displayName) {
	}

	public static final CustomPayload.Id<SyncDefinitionsPayload> ID =
			new CustomPayload.Id<>(Identifier.of(TudursGuns.MOD_ID, "sync_definitions"));

	public static final PacketCodec<RegistryByteBuf, SyncDefinitionsPayload> CODEC = PacketCodec.of(
			(value, buf) -> {
				buf.writeString(value.kind());
				buf.writeVarInt(value.entries().size());
				for (Entry entry : value.entries()) {
					buf.writeIdentifier(entry.id());
					buf.writeString(entry.definitionJson());
					buf.writeString(entry.displayName());
				}
			},
			buf -> {
				String kind = buf.readString();
				int count = buf.readVarInt();
				List<Entry> entries = new ArrayList<>(count);
				for (int i = 0; i < count; i++) {
					entries.add(new Entry(buf.readIdentifier(), buf.readString(), buf.readString()));
				}
				return new SyncDefinitionsPayload(kind, entries);
			}
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
