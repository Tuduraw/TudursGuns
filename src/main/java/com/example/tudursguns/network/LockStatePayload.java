package com.example.tudursguns.network;

import com.example.tudursguns.TudursGuns;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C: the shooter's own missile lock-on state, sent whenever it changes. targetId is -1 when
 * nothing is being tracked. */
public record LockStatePayload(int targetId, int progressTicks, int requiredTicks) implements CustomPayload {

	public static final LockStatePayload NONE = new LockStatePayload(-1, 0, 0);

	public static final CustomPayload.Id<LockStatePayload> ID =
			new CustomPayload.Id<>(Identifier.of(TudursGuns.MOD_ID, "lock_state"));

	public static final PacketCodec<RegistryByteBuf, LockStatePayload> CODEC = PacketCodec.of(
			(value, buf) -> {
				buf.writeVarInt(value.targetId());
				buf.writeVarInt(value.progressTicks());
				buf.writeVarInt(value.requiredTicks());
			},
			buf -> new LockStatePayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
