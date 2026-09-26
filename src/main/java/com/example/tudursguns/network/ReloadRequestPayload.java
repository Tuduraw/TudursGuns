package com.example.tudursguns.network;

import com.example.tudursguns.TudursGuns;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S: the reload key was pressed. The server decides which held weapon (if any) reloads. */
public record ReloadRequestPayload() implements CustomPayload {

	public static final ReloadRequestPayload INSTANCE = new ReloadRequestPayload();

	public static final CustomPayload.Id<ReloadRequestPayload> ID =
			new CustomPayload.Id<>(Identifier.of(TudursGuns.MOD_ID, "reload_request"));

	public static final PacketCodec<RegistryByteBuf, ReloadRequestPayload> CODEC = PacketCodec.unit(INSTANCE);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
