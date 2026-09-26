package com.example.tudursguns.handheld;

import com.example.tudursvehiclemod.asset.WeaponStats;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

/** The handful of weapon-file values the client needs (HUD, tooltip, item name), taken from the
 * server's WeaponStats and sent along with each definition. */
public record WeaponSummary(
		String weaponType,
		String displayName,
		int magazineSize,
		int reloadTicks,
		int cooldownTicks,
		float velocity,
		float gravity,
		int lockTimeTicks,
		float lockTimePerBlock,
		int modeCount
) {

	public static WeaponSummary of(WeaponStats stats) {
		return new WeaponSummary(stats.weaponType().name(), stats.displayName(), stats.magazineSize(),
				stats.reloadTicks(), stats.cooldownTicks(), stats.velocity(), stats.gravity(),
				stats.lockTimeTicks(), (float) stats.lockTimePerBlock(), Math.max(1, stats.modeNum()));
	}

	public static final PacketCodec<RegistryByteBuf, WeaponSummary> PACKET_CODEC = PacketCodec.of(
			(value, buf) -> {
				buf.writeString(value.weaponType());
				buf.writeString(value.displayName());
				buf.writeVarInt(value.magazineSize());
				buf.writeVarInt(value.reloadTicks());
				buf.writeVarInt(value.cooldownTicks());
				buf.writeFloat(value.velocity());
				buf.writeFloat(value.gravity());
				buf.writeVarInt(value.lockTimeTicks());
				buf.writeFloat(value.lockTimePerBlock());
				buf.writeVarInt(value.modeCount());
			},
			buf -> new WeaponSummary(buf.readString(), buf.readString(), buf.readVarInt(), buf.readVarInt(),
					buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readFloat(),
					buf.readVarInt())
	);
}
