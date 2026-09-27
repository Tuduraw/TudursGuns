package com.example.tudursguns.client.render;

import com.example.tudursguns.handheld.AnimationDefinition;
import com.example.tudursguns.weapon.WeaponAnimationEvents;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Where each animated part of a weapon is right now: its counters' accumulated positions plus any
 * sequences still playing (see AnimationDefinition). Each part's offset is relative to its parent,
 * so drawing a part means applying its whole chain from the root down. */
public final class WeaponPose {

	/** Events up to this far in the future still count as "just now" - the client's clock can run a
	 * little behind the server's. */
	private static final double CLOCK_SLACK_TICKS = 3.0;

	private final AnimationDefinition animation;
	private final Map<String, Vector3f> translations = new HashMap<>();
	private final Map<String, Quaternionf> rotations = new HashMap<>();

	private WeaponPose(AnimationDefinition animation) {
		this.animation = animation;
	}

	public AnimationDefinition animation() {
		return this.animation;
	}

	/** now: world time plus the frame's tick progress. sequences: false to show only the counters'
	 * resting state (items in an inventory, on the ground, in a frame). */
	public static WeaponPose compute(AnimationDefinition animation, Map<String, WeaponAnimationEvents.Occurrence> events,
			Map<String, Integer> counters, double now, boolean sequences) {
		WeaponPose pose = new WeaponPose(animation);
		for (Map.Entry<String, AnimationDefinition.Counter> entry : animation.counters().entrySet()) {
			pose.addCounter(entry.getValue(), counters.getOrDefault(entry.getKey(), 0), events.get(entry.getValue().on()), now);
		}
		if (sequences) {
			for (Map.Entry<String, AnimationDefinition.Sequence> entry : animation.sequences().entrySet()) {
				WeaponAnimationEvents.Occurrence occurrence = events.get(entry.getKey());
				if (occurrence != null) {
					pose.addSequence(entry.getValue(), occurrence, now);
				}
			}
		}
		return pose;
	}

	private void addCounter(AnimationDefinition.Counter counter, int value, WeaponAnimationEvents.Occurrence lastAdvance, double now) {
		// Slide from the previous count to this one over counter.ticks after the advancing event.
		float shown = value;
		if (lastAdvance != null && counter.ticks() > 0f) {
			double elapsed = now - lastAdvance.time();
			if (elapsed >= -CLOCK_SLACK_TICKS && elapsed < counter.ticks()) {
				float t = AnimationDefinition.Easing.SMOOTH.apply((float) Math.max(0.0, elapsed / counter.ticks()));
				shown = value - counter.add() + counter.add() * t;
			}
		}
		if (shown == 0f) {
			return;
		}
		add(counter.part(), new Vector3f(counter.translationPerStep()).mul(shown), new Vector3f(counter.rotationPerStep()).mul(shown));
	}

	private void addSequence(AnimationDefinition.Sequence sequence, WeaponAnimationEvents.Occurrence occurrence, double now) {
		float length = sequence.length();
		double elapsed = now - occurrence.time();
		if (elapsed < -CLOCK_SLACK_TICKS) {
			return;
		}
		elapsed = Math.max(0.0, elapsed);
		double scale = sequence.fitToEvent() && occurrence.duration() > 0 && length > 0f ? occurrence.duration() / (double) length : 1.0;
		float t = (float) (elapsed / scale);
		if (t > length) {
			return;
		}
		for (AnimationDefinition.Track track : sequence.tracks()) {
			evaluate(track, t);
		}
	}

	/** The track's offset at time t: between the keyframes around t (from rest before the first one),
	 * nothing once its last keyframe has passed. */
	private void evaluate(AnimationDefinition.Track track, float t) {
		List<AnimationDefinition.Keyframe> keyframes = new ArrayList<>(track.keyframes());
		if (keyframes.isEmpty()) {
			return;
		}
		keyframes.sort(Comparator.comparingDouble(AnimationDefinition.Keyframe::tick));
		if (t > keyframes.get(keyframes.size() - 1).tick()) {
			return;
		}
		float previousTick = 0f;
		Vector3fc previousTranslation = new Vector3f();
		Vector3fc previousRotation = new Vector3f();
		for (AnimationDefinition.Keyframe keyframe : keyframes) {
			if (t <= keyframe.tick()) {
				float span = keyframe.tick() - previousTick;
				float progress = span <= 0f ? 1f : keyframe.easing().apply(Math.max(0f, Math.min(1f, (t - previousTick) / span)));
				add(track.part(), previousTranslation.lerp(keyframe.translation(), progress, new Vector3f()),
						previousRotation.lerp(keyframe.rotation(), progress, new Vector3f()));
				return;
			}
			previousTick = keyframe.tick();
			previousTranslation = keyframe.translation();
			previousRotation = keyframe.rotation();
		}
	}

	private void add(String part, Vector3f translation, Vector3f rotationDegrees) {
		this.translations.computeIfAbsent(part, key -> new Vector3f()).add(translation);
		Quaternionf rotation = new Quaternionf().rotationXYZ((float) Math.toRadians(rotationDegrees.x),
				(float) Math.toRadians(rotationDegrees.y), (float) Math.toRadians(rotationDegrees.z));
		this.rotations.computeIfAbsent(part, key -> new Quaternionf()).mul(rotation);
	}

	/** The whole weapon's own offset (tilting, recoil). Applied once, before the weapon and arms. */
	public void applyRoot(MatrixStack matrices) {
		applyLocal(matrices, AnimationDefinition.ROOT);
	}

	/** Every offset from just below the root down to part (the root itself is already applied). */
	public void applyChain(MatrixStack matrices, String part) {
		List<String> chain = new ArrayList<>();
		String current = part;
		while (current != null && !AnimationDefinition.ROOT.equals(current) && chain.size() < 16 && !chain.contains(current)) {
			chain.add(current);
			current = this.animation.parentOf(current);
		}
		for (int i = chain.size() - 1; i >= 0; i--) {
			applyLocal(matrices, chain.get(i));
		}
	}

	private void applyLocal(MatrixStack matrices, String part) {
		Vector3f translation = this.translations.get(part);
		Quaternionf rotation = this.rotations.get(part);
		if (translation == null && rotation == null) {
			return;
		}
		Vector3f pivot = this.animation.pivotOf(part);
		if (translation != null) {
			matrices.translate(translation.x, translation.y, translation.z);
		}
		if (rotation != null) {
			matrices.translate(pivot.x, pivot.y, pivot.z);
			matrices.multiply(rotation);
			matrices.translate(-pivot.x, -pivot.y, -pivot.z);
		}
	}
}
