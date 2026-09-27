package com.example.tudursguns.handheld;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;
import java.util.function.Consumer;

/** How carrying something changes the holder's walking speed - read from the same JSON object as
 * the rest of its definition (weapons, attachments, throwables, mines, equipment):
 *
 * movement_speed: while it's in either hand. aiming_movement_speed: added on top while aiming it
 * (weapons and their attachments only).
 *
 * Both are fractions of the holder's speed, like armor's movement_speed: -0.1 = 10% slower, 0.2 =
 * 20% faster. Everything held (both hands, a weapon's fitted attachments) adds up; the total can't
 * go below -1 (standing still). Leave them out for no change. */
public record HeldMovement(float speed, float aimingSpeed) {

	public static final MapCodec<HeldMovement> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.floatRange(-1f, 10f).optionalFieldOf("movement_speed", 0f).forGetter(HeldMovement::speed),
			Codec.floatRange(-1f, 10f).optionalFieldOf("aiming_movement_speed", 0f).forGetter(HeldMovement::aimingSpeed)
	).apply(instance, HeldMovement::new));

	public float total(boolean aiming) {
		return aiming ? this.speed + this.aimingSpeed : this.speed;
	}

	/** Tooltip lines ("Movement speed -10%", "While aiming -25%"), none when there's no effect. */
	public void appendTooltip(Consumer<Text> textConsumer) {
		line(textConsumer, "tooltip.tudursguns.movement_speed", this.speed);
		line(textConsumer, "tooltip.tudursguns.aiming_movement_speed", this.aimingSpeed);
	}

	private static void line(Consumer<Text> textConsumer, String key, float value) {
		if (value == 0f) {
			return;
		}
		String percent = String.format(Locale.ROOT, "%+d%%", Math.round(value * 100f));
		textConsumer.accept(Text.translatable(key, percent)
				.formatted(value < 0f ? Formatting.RED : Formatting.GREEN));
	}
}
