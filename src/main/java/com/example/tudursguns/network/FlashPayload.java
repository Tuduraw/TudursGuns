package com.example.tudursguns.network;

import com.example.tudursguns.TudursGuns;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C: a flash went off in view - white out the screen at intensity (0-1), fading over durationTicks. */
public record FlashPayload(float intensity, int durationTicks) implements CustomPayload {

	public static final CustomPayload.Id<FlashPayload> ID = new CustomPayload.Id<>(Identifier.of(TudursGuns.MOD_ID, "flash"));

	public static final PacketCodec<RegistryByteBuf, FlashPayload> CODEC = PacketCodec.of(
			(value, buf) -> {
				buf.writeFloat(value.intensity());
				buf.writeVarInt(value.durationTicks());
			},
			buf -> new FlashPayload(buf.readFloat(), buf.readVarInt())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
