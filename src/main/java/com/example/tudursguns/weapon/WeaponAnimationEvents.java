package com.example.tudursguns.weapon;

import com.example.tudursguns.handheld.AnimationDefinition;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.registry.ModComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

	private record ScheduledSound(UUID player, long due, AnimationDefinition.SoundCue cue) {
	}

	private static final List<ScheduledSound> SCHEDULED = new ArrayList<>();

	/** Plays the sequence sounds that are due. Called at the end of every server tick. */
	public static void tick(MinecraftServer server) {
		if (SCHEDULED.isEmpty()) {
			return;
		}
		Iterator<ScheduledSound> it = SCHEDULED.iterator();
		while (it.hasNext()) {
			ScheduledSound scheduled = it.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(scheduled.player());
			if (player == null) {
				it.remove();
				continue;
			}
			if (player.getEntityWorld().getTime() >= scheduled.due()) {
				it.remove();
				HandheldCombat.playSound(player, scheduled.cue().sound(), player.getEyePos(), scheduled.cue().volume(),
						scheduled.cue().pitch(), 0.05f);
			}
		}
	}

	public static void trigger(ItemStack stack, World world, String event) {
		trigger(stack, world, event, 0);
	}

	public static void trigger(ItemStack stack, World world, String event, int duration) {
		trigger(null, stack, world, event, duration);
	}

	public static void trigger(PlayerEntity player, ItemStack stack, World world, String event) {
		trigger(player, stack, world, event, 0);
	}

	/** With a player, the event's sequence sounds are played (from that player) at their ticks. */
	public static void trigger(PlayerEntity player, ItemStack stack, World world, String event, int duration) {
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
		if (player != null && sequence != null && !sequence.sounds().isEmpty()) {
			double scale = sequence.timeScale(duration);
			for (AnimationDefinition.SoundCue cue : sequence.sounds()) {
				SCHEDULED.add(new ScheduledSound(player.getUuid(), world.getTime() + Math.round(cue.tick() * scale), cue));
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
