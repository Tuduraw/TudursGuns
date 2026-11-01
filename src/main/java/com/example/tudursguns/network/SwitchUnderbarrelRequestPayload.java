package com.example.tudursguns.network;

import com.example.tudursguns.TudursGuns;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S: the underbarrel switch key was pressed - toggles the held weapon between its own fire and its underbarrel launcher. */
public record SwitchUnderbarrelRequestPayload() implements CustomPacketPayload {

	public static final SwitchUnderbarrelRequestPayload INSTANCE = new SwitchUnderbarrelRequestPayload();

	public static final CustomPacketPayload.Type<SwitchUnderbarrelRequestPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, "switch_underbarrel_request"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SwitchUnderbarrelRequestPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
