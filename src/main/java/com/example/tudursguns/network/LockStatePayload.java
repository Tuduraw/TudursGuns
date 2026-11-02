package com.example.tudursguns.network;

import com.example.tudursguns.TudursGuns;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** S2C: the shooter's own missile lock-on state, sent whenever it changes. targetId is -1 when
 * nothing is being tracked. */
public record LockStatePayload(int targetId, int progressTicks, int requiredTicks) implements CustomPacketPayload {

	public static final LockStatePayload NONE = new LockStatePayload(-1, 0, 0);

	public static final CustomPacketPayload.Type<LockStatePayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, "lock_state"));

	public static final StreamCodec<RegistryFriendlyByteBuf, LockStatePayload> CODEC = StreamCodec.ofMember(
			(value, buf) -> {
				buf.writeVarInt(value.targetId());
				buf.writeVarInt(value.progressTicks());
				buf.writeVarInt(value.requiredTicks());
			},
			buf -> new LockStatePayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
