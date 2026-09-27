package com.example.tudursguns.handheld;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A weapon's moving parts and their motions - the "animation" section of a handheld definition
 * (the keys are described in the README):
 * - parts: groups of the OBJ model with a pivot and a parent ("root" is the whole weapon; the arms
 *   are the parts "right_arm" and "left_arm");
 * - sequences: keyframed motions played when an Event happens, optionally with sounds;
 * - counters: state that builds up and stays on the stack (a revolver's cylinder);
 * - ammo_poses: poses keyed on the number of rounds loaded.
 * Only the model's pose changes; nothing here affects firing. */
public record AnimationDefinition(
		Map<String, Part> parts,
		Map<String, Sequence> sequences,
		Map<String, Counter> counters,
		List<Track> ammoPoses
) {

	public static final String ROOT = "root";
	public static final String RIGHT_ARM = "right_arm";
	public static final String LEFT_ARM = "left_arm";

	public static final Codec<AnimationDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(Codec.STRING, Part.CODEC).optionalFieldOf("parts", Map.of()).forGetter(AnimationDefinition::parts),
			Codec.unboundedMap(Codec.STRING, Sequence.CODEC).optionalFieldOf("sequences", Map.of()).forGetter(AnimationDefinition::sequences),
			Codec.unboundedMap(Codec.STRING, Counter.CODEC).optionalFieldOf("counters", Map.of()).forGetter(AnimationDefinition::counters),
			Track.CODEC.listOf().optionalFieldOf("ammo_poses", List.of()).forGetter(AnimationDefinition::ammoPoses)
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

	/** groups: OBJ group names; parent: another part (default root); pivot: model-space point;
	 * translation/rotation: always applied; aiming_translation/aiming_rotation: applied as it's raised. */
	public record Part(List<String> groups, Optional<String> parent, Vector3f pivot, Vector3f translation, Vector3f rotation,
			Vector3f aimingTranslation, Vector3f aimingRotation) {

		public static final Codec<Part> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.listOf().optionalFieldOf("groups", List.of()).forGetter(Part::groups),
				Codec.STRING.optionalFieldOf("parent").forGetter(Part::parent),
				DefinitionCodecs.VECTOR_3F.optionalFieldOf("pivot", new Vector3f()).forGetter(Part::pivot),
				DefinitionCodecs.VECTOR_3F.optionalFieldOf("translation", new Vector3f()).forGetter(Part::translation),
				DefinitionCodecs.VECTOR_3F.optionalFieldOf("rotation", new Vector3f()).forGetter(Part::rotation),
				DefinitionCodecs.VECTOR_3F.optionalFieldOf("aiming_translation", new Vector3f()).forGetter(Part::aimingTranslation),
				DefinitionCodecs.VECTOR_3F.optionalFieldOf("aiming_rotation", new Vector3f()).forGetter(Part::aimingRotation)
		).apply(instance, Part::new));
	}

	public record Sequence(boolean fitToEvent, List<Track> tracks, List<SoundCue> sounds) {

		public static final Codec<Sequence> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.BOOL.optionalFieldOf("fit_to_event", false).forGetter(Sequence::fitToEvent),
				Track.CODEC.listOf().optionalFieldOf("tracks", List.of()).forGetter(Sequence::tracks),
				SoundCue.CODEC.listOf().optionalFieldOf("sounds", List.of()).forGetter(Sequence::sounds)
		).apply(instance, Sequence::new));

		/** Tick of the last keyframe or sound (the sequence's own length). */
		public float length() {
			float length = 0f;
			for (Track track : this.tracks) {
				for (Keyframe keyframe : track.keyframes()) {
					length = Math.max(length, keyframe.tick());
				}
			}
			for (SoundCue sound : this.sounds) {
				length = Math.max(length, sound.tick());
			}
			return length;
		}

		/** How much fit_to_event stretches this sequence for an event lasting duration ticks (1 = none). */
		public double timeScale(int duration) {
			float length = length();
			return this.fitToEvent && duration > 0 && length > 0f ? duration / (double) length : 1.0;
		}
	}

	/** A named sound (like a weapon file's Sound) at a tick of a sequence. */
	public record SoundCue(float tick, String sound, float volume, float pitch) {

		public static final Codec<SoundCue> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.floatRange(0f, 72000f).optionalFieldOf("tick", 0f).forGetter(SoundCue::tick),
				Codec.STRING.fieldOf("sound").forGetter(SoundCue::sound),
				Codec.floatRange(0f, 100f).optionalFieldOf("volume", 1f).forGetter(SoundCue::volume),
				Codec.floatRange(0f, 10f).optionalFieldOf("pitch", 1f).forGetter(SoundCue::pitch)
		).apply(instance, SoundCue::new));
	}

	public record Track(String part, List<Keyframe> keyframes) {

		/** Keyframes in tick order. */
		public Track {
			keyframes = keyframes.stream().sorted(Comparator.comparingDouble(Keyframe::tick)).toList();
		}

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
				DefinitionCodecs.VECTOR_3F.optionalFieldOf("translation", new Vector3f()).forGetter(Keyframe::translation),
				DefinitionCodecs.VECTOR_3F.optionalFieldOf("rotation", new Vector3f()).forGetter(Keyframe::rotation),
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
				DefinitionCodecs.VECTOR_3F.optionalFieldOf("translation_per_step", new Vector3f()).forGetter(Counter::translationPerStep),
				DefinitionCodecs.VECTOR_3F.optionalFieldOf("rotation_per_step", new Vector3f()).forGetter(Counter::rotationPerStep)
		).apply(instance, Counter::new));

		public int advance(int value) {
			int next = value + this.add;
			return this.modulo > 0 ? Math.floorMod(next, this.modulo) : next;
		}
	}

	public enum Easing {
		LINEAR, SMOOTH, EASE_IN, EASE_OUT, STEP;

		public static final Codec<Easing> CODEC = DefinitionCodecs.lenientEnum(Easing.class, SMOOTH);

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
