package com.example.tudursguns.network;

import com.example.tudursguns.TudursGuns;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S: the mode switch key was pressed - cycles the held weapon's ModeNum mode. */
public record SwitchModeRequestPayload() implements CustomPayload {

	public static final SwitchModeRequestPayload INSTANCE = new SwitchModeRequestPayload();

	public static final CustomPayload.Id<SwitchModeRequestPayload> ID =
			new CustomPayload.Id<>(Identifier.of(TudursGuns.MOD_ID, "switch_mode_request"));

	public static final PacketCodec<RegistryByteBuf, SwitchModeRequestPayload> CODEC = PacketCodec.unit(INSTANCE);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
