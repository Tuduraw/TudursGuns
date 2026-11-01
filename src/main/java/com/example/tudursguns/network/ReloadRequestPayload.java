package com.example.tudursguns.network;

import com.example.tudursguns.TudursGuns;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S: the reload key was pressed. The server decides which held weapon (if any) reloads. */
public record ReloadRequestPayload() implements CustomPacketPayload {

	public static final ReloadRequestPayload INSTANCE = new ReloadRequestPayload();

	public static final CustomPacketPayload.Type<ReloadRequestPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, "reload_request"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ReloadRequestPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
