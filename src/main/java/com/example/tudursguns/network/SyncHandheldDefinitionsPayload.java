package com.example.tudursguns.network;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.handheld.WeaponSummary;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** S2C: every handheld definition the server has loaded, sent on join and after every data reload.
 * Each definition travels as its own JSON (re-parsed with HandheldDefinition.CODEC on the client),
 * together with the weapon-file values the client displays. */
public record SyncHandheldDefinitionsPayload(List<Entry> entries) implements CustomPayload {

	public record Entry(Identifier id, String definitionJson, WeaponSummary weapon) {
	}

	public static final CustomPayload.Id<SyncHandheldDefinitionsPayload> ID =
			new CustomPayload.Id<>(Identifier.of(TudursGuns.MOD_ID, "sync_handheld_definitions"));

	public static final PacketCodec<RegistryByteBuf, SyncHandheldDefinitionsPayload> CODEC = PacketCodec.of(
			(value, buf) -> {
				buf.writeVarInt(value.entries().size());
				for (Entry entry : value.entries()) {
					buf.writeIdentifier(entry.id());
					buf.writeString(entry.definitionJson());
					WeaponSummary.PACKET_CODEC.encode(buf, entry.weapon());
				}
			},
			buf -> {
				int count = buf.readVarInt();
				List<Entry> entries = new ArrayList<>(count);
				for (int i = 0; i < count; i++) {
					entries.add(new Entry(buf.readIdentifier(), buf.readString(), WeaponSummary.PACKET_CODEC.decode(buf)));
				}
				return new SyncHandheldDefinitionsPayload(entries);
			}
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
