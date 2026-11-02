package com.example.tudursguns.handheld;

import com.example.tudursvehiclemod.asset.WeaponStats;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The weapon-file values a client needs (HUD, tooltip, creative tab, throw guide), sent with each
 * definition that names a weapon file - on a dedicated server the file may exist only there. */
public record WeaponSummary(int magazineSize, int reloadTicks, int modeCount, float gravity) {

	public static WeaponSummary of(WeaponStats stats) {
		return new WeaponSummary(stats.magazineSize(), stats.reloadTicks(), Math.max(1, stats.modeNum()), stats.gravity());
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, WeaponSummary> PACKET_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, WeaponSummary::magazineSize,
			ByteBufCodecs.VAR_INT, WeaponSummary::reloadTicks,
			ByteBufCodecs.VAR_INT, WeaponSummary::modeCount,
			ByteBufCodecs.FLOAT, WeaponSummary::gravity,
			WeaponSummary::new);
}
