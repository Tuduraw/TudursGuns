package com.example.tudursguns.soldier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/** One point of a soldier post's patrol route: a block position relative to the post, and how long
 * (ticks) the soldier waits there before walking on. */
public record SoldierWaypoint(int x, int y, int z, int waitTicks) {

	/** Farthest a point may be from its post, on each axis. */
	public static final int MAX_DISTANCE = 128;
	public static final int MAX_WAIT_TICKS = 6000;

	public static final Codec<SoldierWaypoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("x").forGetter(SoldierWaypoint::x),
			Codec.INT.fieldOf("y").forGetter(SoldierWaypoint::y),
			Codec.INT.fieldOf("z").forGetter(SoldierWaypoint::z),
			Codec.INT.optionalFieldOf("wait", 0).forGetter(SoldierWaypoint::waitTicks)
	).apply(instance, SoldierWaypoint::new));

	public static final PacketCodec<ByteBuf, SoldierWaypoint> PACKET_CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, SoldierWaypoint::x,
			PacketCodecs.VAR_INT, SoldierWaypoint::y,
			PacketCodecs.VAR_INT, SoldierWaypoint::z,
			PacketCodecs.VAR_INT, SoldierWaypoint::waitTicks,
			SoldierWaypoint::new);

	/** This point with every value brought into range (a route sent by a client is not trusted). */
	public SoldierWaypoint clamped() {
		return new SoldierWaypoint(MathHelper.clamp(this.x, -MAX_DISTANCE, MAX_DISTANCE), MathHelper.clamp(this.y, -MAX_DISTANCE, MAX_DISTANCE),
				MathHelper.clamp(this.z, -MAX_DISTANCE, MAX_DISTANCE), MathHelper.clamp(this.waitTicks, 0, MAX_WAIT_TICKS));
	}

	public BlockPos absolute(BlockPos post) {
		return post.add(this.x, this.y, this.z);
	}
}
