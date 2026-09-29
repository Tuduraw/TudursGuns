package com.example.tudursguns.network;

import com.example.tudursguns.TudursGuns;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C: another player (by entity id) started or stopped holding the aim key. */
public record PlayerAimPayload(int entityId, boolean held) implements CustomPayload {

	public static final CustomPayload.Id<PlayerAimPayload> ID =
			new CustomPayload.Id<>(Identifier.of(TudursGuns.MOD_ID, "player_aim"));

	public static final PacketCodec<RegistryByteBuf, PlayerAimPayload> CODEC = PacketCodec.of(
			(value, buf) -> {
				buf.writeVarInt(value.entityId());
				buf.writeBoolean(value.held());
			},
			buf -> new PlayerAimPayload(buf.readVarInt(), buf.readBoolean())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
