package com.example.tudursguns.soldier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

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

	public static final StreamCodec<ByteBuf, SoldierWaypoint> PACKET_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SoldierWaypoint::x,
			ByteBufCodecs.VAR_INT, SoldierWaypoint::y,
			ByteBufCodecs.VAR_INT, SoldierWaypoint::z,
			ByteBufCodecs.VAR_INT, SoldierWaypoint::waitTicks,
			SoldierWaypoint::new);

	/** This point with every value brought into range (a route sent by a client is not trusted). */
	public SoldierWaypoint clamped() {
		return new SoldierWaypoint(Mth.clamp(this.x, -MAX_DISTANCE, MAX_DISTANCE), Mth.clamp(this.y, -MAX_DISTANCE, MAX_DISTANCE),
				Mth.clamp(this.z, -MAX_DISTANCE, MAX_DISTANCE), Mth.clamp(this.waitTicks, 0, MAX_WAIT_TICKS));
	}

	public BlockPos absolute(BlockPos post) {
		return post.offset(this.x, this.y, this.z);
	}
}
