package com.example.tudursguns.network;

import com.example.tudursguns.TudursGuns;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** S2C: every attachment definition the server has loaded, each as its own JSON (re-parsed with
 * AttachmentDefinition.CODEC on the client). Sent together with SyncHandheldDefinitionsPayload. */
public record SyncAttachmentDefinitionsPayload(List<Entry> entries) implements CustomPayload {

	public record Entry(Identifier id, String definitionJson) {
	}

	public static final CustomPayload.Id<SyncAttachmentDefinitionsPayload> ID =
			new CustomPayload.Id<>(Identifier.of(TudursGuns.MOD_ID, "sync_attachment_definitions"));

	public static final PacketCodec<RegistryByteBuf, SyncAttachmentDefinitionsPayload> CODEC = PacketCodec.of(
			(value, buf) -> {
				buf.writeVarInt(value.entries().size());
				for (Entry entry : value.entries()) {
					buf.writeIdentifier(entry.id());
					buf.writeString(entry.definitionJson());
				}
			},
			buf -> {
				int count = buf.readVarInt();
				List<Entry> entries = new ArrayList<>(count);
				for (int i = 0; i < count; i++) {
					entries.add(new Entry(buf.readIdentifier(), buf.readString()));
				}
				return new SyncAttachmentDefinitionsPayload(entries);
			}
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
