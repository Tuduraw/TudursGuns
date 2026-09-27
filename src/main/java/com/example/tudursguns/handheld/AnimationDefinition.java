package com.example.tudursguns.handheld;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** A weapon's moving parts and their motions - the "animation" section of a handheld definition.
 *
 * parts: named pieces of the weapon's OBJ model (by OBJ group), each with a pivot (the point it turns
 *   about, in model space) and a parent. Everything moves with its parent: "root" is the whole weapon
 *   (every group not given to another part), so e.g. the hammer can be a child of the frame, and
 *   tilting "root" tilts everything - including, in first person, the arms. The arms can be moved
 *   themselves as the parts "right_arm" and "left_arm" (children of root; pivot = their own origin).
 *
 * sequences: motions played when something happens to the weapon (see Event for the names: fire,
 *   reload, ...). Each is a list of tracks; a track moves one part through keyframes - at tick t
 *   (counted from the event), be translated/rotated this much from rest. Values between keyframes are
 *   interpolated (the later keyframe's easing), the part starts from rest at tick 0 unless a keyframe
 *   says otherwise, and once the last keyframe is passed the track no longer applies - so a pull-and-
 *   release is keyframes rest -> pulled -> rest. fit_to_event stretches the whole sequence to the
 *   event's actual duration (a reload, which attachments can make faster or slower).
 *   Several sequences can play at once; their offsets add up.
 *
 * counters: state that builds up and stays - a revolver's cylinder turning one chamber per shot. On
 *   each "on" event the counter goes up by add (wrapping at modulo, if set), and its part is posed at
 *   counter x rotation_per_step / translation_per_step, moving to the new position over ticks.
 *   reset_on events put it back to 0. The count is kept on the weapon itself, so it survives saving
 *   and is the same for everyone looking.
 *
 * Only the model poses change; nothing here affects firing. Sequences play in hand (first and third
 * person); counters show everywhere, the inventory included. */
public record AnimationDefinition(
		Map<String, Part> parts,
		Map<String, Sequence> sequences,
		Map<String, Counter> counters
) {

	public static final String ROOT = "root";
	public static final String RIGHT_ARM = "right_arm";
	public static final String LEFT_ARM = "left_arm";

	public static final Codec<AnimationDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(Codec.STRING, Part.CODEC).optionalFieldOf("parts", Map.of()).forGetter(AnimationDefinition::parts),
			Codec.unboundedMap(Codec.STRING, Sequence.CODEC).optionalFieldOf("sequences", Map.of()).forGetter(AnimationDefinition::sequences),
			Codec.unboundedMap(Codec.STRING, Counter.CODEC).optionalFieldOf("counters", Map.of()).forGetter(AnimationDefinition::counters)
	).apply(instance, AnimationDefinition::new));

	/** A part's parent ("root" by default; root itself has none). */
	public String parentOf(String part) {
		if (ROOT.equals(part)) {
			return null;
		}
		Part def = this.parts.get(part);
		return def == null ? ROOT : def.parent().orElse(ROOT);
	}

	public Vector3f pivotOf(String part) {
		Part def = this.parts.get(part);
		return def == null ? new Vector3f() : def.pivot();
	}

	/** groups: OBJ group names; parent: another part (default root); pivot: model-space point. */
	public record Part(List<String> groups, Optional<String> parent, Vector3f pivot) {

		public static final Codec<Part> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.listOf().optionalFieldOf("groups", List.of()).forGetter(Part::groups),
				Codec.STRING.optionalFieldOf("parent").forGetter(Part::parent),
				HandheldDefinition.VECTOR_3F.optionalFieldOf("pivot", new Vector3f()).forGetter(Part::pivot)
		).apply(instance, Part::new));
	}

	public record Sequence(boolean fitToEvent, List<Track> tracks) {

		public static final Codec<Sequence> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.BOOL.optionalFieldOf("fit_to_event", false).forGetter(Sequence::fitToEvent),
				Track.CODEC.listOf().fieldOf("tracks").forGetter(Sequence::tracks)
		).apply(instance, Sequence::new));

		/** Tick of the last keyframe of any track (the sequence's own length). */
		public float length() {
			float length = 0f;
			for (Track track : this.tracks) {
				for (Keyframe keyframe : track.keyframes()) {
					length = Math.max(length, keyframe.tick());
				}
			}
			return length;
		}
	}

	public record Track(String part, List<Keyframe> keyframes) {

		public static final Codec<Track> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("part").forGetter(Track::part),
				Keyframe.CODEC.listOf().fieldOf("keyframes").forGetter(Track::keyframes)
		).apply(instance, Track::new));
	}

	/** tick: from the event (fractions allowed). translation (blocks) / rotation (degrees, X then Y then
	 * Z) from the part's rest pose. easing: how the move INTO this keyframe goes. */
	public record Keyframe(float tick, Vector3f translation, Vector3f rotation, Easing easing) {

		public static final Codec<Keyframe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.floatRange(0f, 72000f).fieldOf("tick").forGetter(Keyframe::tick),
				HandheldDefinition.VECTOR_3F.optionalFieldOf("translation", new Vector3f()).forGetter(Keyframe::translation),
				HandheldDefinition.VECTOR_3F.optionalFieldOf("rotation", new Vector3f()).forGetter(Keyframe::rotation),
				Easing.CODEC.optionalFieldOf("easing", Easing.SMOOTH).forGetter(Keyframe::easing)
		).apply(instance, Keyframe::new));
	}

	/** on: the event that advances it; add: by how much; modulo: wraps back to 0 at this (0 = never);
	 * reset_on: events that put it back to 0; ticks: how long the move to a new position takes;
	 * part + translation_per_step / rotation_per_step: how each count poses the part. */
	public record Counter(String on, int add, int modulo, List<String> resetOn, float ticks, String part,
			Vector3f translationPerStep, Vector3f rotationPerStep) {

		public static final Codec<Counter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("on").forGetter(Counter::on),
				Codec.INT.optionalFieldOf("add", 1).forGetter(Counter::add),
				Codec.intRange(0, 100000).optionalFieldOf("modulo", 0).forGetter(Counter::modulo),
				Codec.STRING.listOf().optionalFieldOf("reset_on", List.of()).forGetter(Counter::resetOn),
				Codec.floatRange(0f, 72000f).optionalFieldOf("ticks", 2f).forGetter(Counter::ticks),
				Codec.STRING.fieldOf("part").forGetter(Counter::part),
				HandheldDefinition.VECTOR_3F.optionalFieldOf("translation_per_step", new Vector3f()).forGetter(Counter::translationPerStep),
				HandheldDefinition.VECTOR_3F.optionalFieldOf("rotation_per_step", new Vector3f()).forGetter(Counter::rotationPerStep)
		).apply(instance, Counter::new));

		public int advance(int value) {
			int next = value + this.add;
			return this.modulo > 0 ? Math.floorMod(next, this.modulo) : next;
		}
	}

	public enum Easing {
		LINEAR, SMOOTH, EASE_IN, EASE_OUT, STEP;

		public static final Codec<Easing> CODEC = Codec.STRING.xmap(
				s -> switch (s.toLowerCase(Locale.ROOT)) {
					case "linear" -> LINEAR;
					case "ease_in" -> EASE_IN;
					case "ease_out" -> EASE_OUT;
					case "step" -> STEP;
					default -> SMOOTH;
				},
				easing -> easing.name().toLowerCase(Locale.ROOT));

		/** t in 0-1 -> eased 0-1. step jumps at the end. */
		public float apply(float t) {
			return switch (this) {
				case LINEAR -> t;
				case SMOOTH -> t * t * (3f - 2f * t);
				case EASE_IN -> t * t;
				case EASE_OUT -> 1f - (1f - t) * (1f - t);
				case STEP -> t >= 1f ? 1f : 0f;
			};
		}
	}

	/** The events weapons report (sequence names / counter "on" / "reset_on"). */
	public static final class Event {
		private Event() {
		}

		/** A shot. */
		public static final String FIRE = "fire";
		/** Pulling the trigger with an empty magazine. */
		public static final String EMPTY = "empty";
		/** A reload starts (its duration is the reload time). */
		public static final String RELOAD = "reload";
		/** A reload finishes. */
		public static final String RELOAD_END = "reload_end";
		/** The ModeNum mode was switched. */
		public static final String MODE = "mode";
		/** The underbarrel launcher was selected or deselected. */
		public static final String UNDERBARREL_SWITCH = "underbarrel_switch";
		/** The underbarrel launcher fired / started reloading ("underbarrel_" + the above). */
		public static final String UNDERBARREL_PREFIX = "underbarrel_";
	}
}
