package com.example.tudursguns.network;

import com.example.tudursguns.TudursGuns;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** S2C: every throwable definition (as JSON), plus its weapon file's display name and gravity - the
 * client needs the gravity for the landing guide. */
public record SyncThrowableDefinitionsPayload(List<Entry> entries) implements CustomPayload {

	public record Entry(Identifier id, String definitionJson, String weaponDisplayName, float gravity) {
	}

	public static final CustomPayload.Id<SyncThrowableDefinitionsPayload> ID =
			new CustomPayload.Id<>(Identifier.of(TudursGuns.MOD_ID, "sync_throwable_definitions"));

	public static final PacketCodec<RegistryByteBuf, SyncThrowableDefinitionsPayload> CODEC = PacketCodec.of(
			(value, buf) -> {
				buf.writeVarInt(value.entries().size());
				for (Entry entry : value.entries()) {
					buf.writeIdentifier(entry.id());
					buf.writeString(entry.definitionJson());
					buf.writeString(entry.weaponDisplayName());
					buf.writeFloat(entry.gravity());
				}
			},
			buf -> {
				int count = buf.readVarInt();
				List<Entry> entries = new ArrayList<>(count);
				for (int i = 0; i < count; i++) {
					entries.add(new Entry(buf.readIdentifier(), buf.readString(), buf.readString(), buf.readFloat()));
				}
				return new SyncThrowableDefinitionsPayload(entries);
			}
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
