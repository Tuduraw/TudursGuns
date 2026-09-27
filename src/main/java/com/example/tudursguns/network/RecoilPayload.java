package com.example.tudursguns.network;

import com.example.tudursguns.TudursGuns;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C: the shooter's own weapon kicked - raise the view by pitch degrees and turn it by yaw. */
public record RecoilPayload(float pitch, float yaw) implements CustomPayload {

	public static final CustomPayload.Id<RecoilPayload> ID = new CustomPayload.Id<>(Identifier.of(TudursGuns.MOD_ID, "recoil"));

	public static final PacketCodec<RegistryByteBuf, RecoilPayload> CODEC = PacketCodec.of(
			(value, buf) -> {
				buf.writeFloat(value.pitch());
				buf.writeFloat(value.yaw());
			},
			buf -> new RecoilPayload(buf.readFloat(), buf.readFloat())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
