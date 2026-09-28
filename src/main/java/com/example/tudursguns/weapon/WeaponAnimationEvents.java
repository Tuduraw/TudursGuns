package com.example.tudursguns.weapon;

import com.example.tudursguns.handheld.AnimationDefinition;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.registry.ModComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/** Records what just happened to a weapon (see AnimationDefinition.Event) on the stack itself, so
 * every client drawing it - the holder in first person, everyone else in third person - plays the
 * same motion at the same time, and advances the definition's counters.
 *
 * Only weapons whose definition has an "animation" section record anything. */
public final class WeaponAnimationEvents {

	private WeaponAnimationEvents() {
	}

	/** When an event last happened (world time) and how long it lasts (ticks; 0 if it has no length). */
	public record Occurrence(long time, int duration) {

		public static final Codec<Occurrence> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.LONG.fieldOf("time").forGetter(Occurrence::time),
				Codec.INT.optionalFieldOf("duration", 0).forGetter(Occurrence::duration)
		).apply(instance, Occurrence::new));
	}

	private static final int EMPTY_REPEAT_TICKS = 10;

	public static void trigger(LivingEntity player, ItemStack stack, String event) {
		trigger(player, stack, event, 0);
	}

	/** Records event on the stack; its sequence's sounds play from the player at their ticks. */
	public static void trigger(LivingEntity player, ItemStack stack, String event, int duration) {
		World world = player.getEntityWorld();
		HandheldDefinition def = ModDefinitions.HANDHELD.getServer(stack.get(ModComponents.WEAPON));
		if (def == null || def.animation().isEmpty()) {
			return;
		}
		AnimationDefinition animation = def.animation().get();
		Map<String, Occurrence> events = new HashMap<>(stack.getOrDefault(ModComponents.ANIM_EVENTS, Map.of()));
		Occurrence last = events.get(event);
		if (event.endsWith(AnimationDefinition.Event.EMPTY) && last != null && world.getTime() - last.time() < EMPTY_REPEAT_TICKS) {
			// Holding the trigger of an empty automatic weapon tries every tick; one click is enough.
			return;
		}
		AnimationDefinition.Sequence sequence = animation.sequences().get(event);
		if (!world.isClient() && sequence != null) {
			double scale = sequence.timeScale(duration);
			for (AnimationDefinition.SoundCue cue : sequence.sounds()) {
				DelayedTasks.schedule(player, Math.round(cue.tick() * scale), target ->
						HandheldCombat.playSound(target, cue.sound(), target.getEyePos(), cue.volume(), cue.pitch(), 0.05f));
			}
		}
		events.put(event, new Occurrence(world.getTime(), duration));
		stack.set(ModComponents.ANIM_EVENTS, Map.copyOf(events));

		if (animation.counters().isEmpty()) {
			return;
		}
		Map<String, Integer> counters = new HashMap<>(stack.getOrDefault(ModComponents.ANIM_COUNTERS, Map.of()));
		boolean changed = false;
		for (Map.Entry<String, AnimationDefinition.Counter> entry : animation.counters().entrySet()) {
			AnimationDefinition.Counter counter = entry.getValue();
			if (counter.resetOn().contains(event)) {
				changed |= counters.remove(entry.getKey()) != null;
			} else if (counter.on().equals(event)) {
				counters.put(entry.getKey(), counter.advance(counters.getOrDefault(entry.getKey(), 0)));
				changed = true;
			}
		}
		if (changed) {
			stack.set(ModComponents.ANIM_COUNTERS, Map.copyOf(counters));
		}
	}

	/** "fire" or "underbarrel_fire" etc., depending on what the stack has selected. */
	public static String forSelection(ItemStack stack, String event) {
		return stack.getOrDefault(ModComponents.ALT_SELECTED, false) ? AnimationDefinition.Event.UNDERBARREL_PREFIX + event : event;
	}
}
