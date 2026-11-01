package com.example.tudursguns.network;

import com.example.tudursguns.TudursGuns;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S: the aim key was pressed or released while holding a weapon. Only needed so OTHER players see
 * the raised pose - aiming by holding use is already visible through vanilla's "using item" state. */
public record AimKeyPayload(boolean held) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<AimKeyPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, "aim_key"));

	public static final StreamCodec<RegistryFriendlyByteBuf, AimKeyPayload> CODEC = StreamCodec.ofMember(
			(value, buf) -> buf.writeBoolean(value.held()),
			buf -> new AimKeyPayload(buf.readBoolean())
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
