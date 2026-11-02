package com.example.tudursguns.network;

import com.example.tudursguns.TudursGuns;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S: the mode switch key was pressed - cycles the held weapon's ModeNum mode. */
public record SwitchModeRequestPayload() implements CustomPacketPayload {

	public static final SwitchModeRequestPayload INSTANCE = new SwitchModeRequestPayload();

	public static final CustomPacketPayload.Type<SwitchModeRequestPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, "switch_mode_request"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SwitchModeRequestPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
