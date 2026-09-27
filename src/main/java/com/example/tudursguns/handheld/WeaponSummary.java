package com.example.tudursguns.handheld;

import com.example.tudursvehiclemod.asset.WeaponStats;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/** The weapon-file values a client needs (HUD, tooltip, creative tab, throw guide), sent with each
 * definition that names a weapon file - on a dedicated server the file may exist only there. */
public record WeaponSummary(int magazineSize, int reloadTicks, int modeCount, float gravity) {

	public static WeaponSummary of(WeaponStats stats) {
		return new WeaponSummary(stats.magazineSize(), stats.reloadTicks(), Math.max(1, stats.modeNum()), stats.gravity());
	}

	public static final PacketCodec<RegistryByteBuf, WeaponSummary> PACKET_CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, WeaponSummary::magazineSize,
			PacketCodecs.VAR_INT, WeaponSummary::reloadTicks,
			PacketCodecs.VAR_INT, WeaponSummary::modeCount,
			PacketCodecs.FLOAT, WeaponSummary::gravity,
			WeaponSummary::new);
}
