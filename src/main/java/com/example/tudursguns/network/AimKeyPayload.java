package com.example.tudursguns.network;

import com.example.tudursguns.TudursGuns;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S: the aim key was pressed or released while holding a weapon. Only needed so OTHER players see
 * the raised pose - aiming by holding use is already visible through vanilla's "using item" state. */
public record AimKeyPayload(boolean held) implements CustomPayload {

	public static final CustomPayload.Id<AimKeyPayload> ID =
			new CustomPayload.Id<>(Identifier.of(TudursGuns.MOD_ID, "aim_key"));

	public static final PacketCodec<RegistryByteBuf, AimKeyPayload> CODEC = PacketCodec.of(
			(value, buf) -> buf.writeBoolean(value.held()),
			buf -> new AimKeyPayload(buf.readBoolean())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
