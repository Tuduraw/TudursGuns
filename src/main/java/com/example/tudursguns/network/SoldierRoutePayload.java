package com.example.tudursguns.network;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.block.SoldierPostBlockEntity;
import com.example.tudursguns.soldier.SoldierWaypoint;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A soldier post's route and engage range. S2C: open the route editor with these. C2S: the editor
 * was closed - save these (the server checks them) and reopen the post's screen. */
public record SoldierRoutePayload(BlockPos pos, List<SoldierWaypoint> route, int engageRange) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<SoldierRoutePayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, "soldier_route"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SoldierRoutePayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, SoldierRoutePayload::pos,
			SoldierWaypoint.PACKET_CODEC.apply(ByteBufCodecs.list(SoldierPostBlockEntity.MAX_WAYPOINTS)), SoldierRoutePayload::route,
			ByteBufCodecs.VAR_INT, SoldierRoutePayload::engageRange,
			SoldierRoutePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
