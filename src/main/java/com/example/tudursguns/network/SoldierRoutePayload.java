package com.example.tudursguns.network;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.block.SoldierPostBlockEntity;
import com.example.tudursguns.soldier.SoldierWaypoint;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/** A soldier post's route and engage range. S2C: open the route editor with these. C2S: the editor
 * was closed - save these (the server checks them) and reopen the post's screen. */
public record SoldierRoutePayload(BlockPos pos, List<SoldierWaypoint> route, int engageRange) implements CustomPayload {

	public static final CustomPayload.Id<SoldierRoutePayload> ID =
			new CustomPayload.Id<>(Identifier.of(TudursGuns.MOD_ID, "soldier_route"));

	public static final PacketCodec<RegistryByteBuf, SoldierRoutePayload> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, SoldierRoutePayload::pos,
			SoldierWaypoint.PACKET_CODEC.collect(PacketCodecs.toList(SoldierPostBlockEntity.MAX_WAYPOINTS)), SoldierRoutePayload::route,
			PacketCodecs.VAR_INT, SoldierRoutePayload::engageRange,
			SoldierRoutePayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
