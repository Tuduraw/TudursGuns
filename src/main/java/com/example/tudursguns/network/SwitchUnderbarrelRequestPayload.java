package com.example.tudursguns.network;

import com.example.tudursguns.TudursGuns;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S: the underbarrel switch key was pressed - toggles the held weapon between its own fire and its underbarrel launcher. */
public record SwitchUnderbarrelRequestPayload() implements CustomPayload {

	public static final SwitchUnderbarrelRequestPayload INSTANCE = new SwitchUnderbarrelRequestPayload();

	public static final CustomPayload.Id<SwitchUnderbarrelRequestPayload> ID =
			new CustomPayload.Id<>(Identifier.of(TudursGuns.MOD_ID, "switch_underbarrel_request"));

	public static final PacketCodec<RegistryByteBuf, SwitchUnderbarrelRequestPayload> CODEC = PacketCodec.unit(INSTANCE);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
