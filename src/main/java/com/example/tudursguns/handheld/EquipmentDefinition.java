package com.example.tudursguns.handheld;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Identifier;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** One piece of support equipment, read from data/<namespace>/equipment/<name>.json. What it does
 * depends on type:
 *
 * binoculars: hold use (or the aim key) to look through; zoom like a scope; rangefinder shows the
 *   distance to what's under the centre of the view.
 * first_aid: hold use for use_ticks to heal heal hit points (plus regeneration_ticks of Regeneration)
 *   - the player looked at within range, else yourself. Uses up one item.
 * repair_kit: hold use while looking at a stationary vehicle within range: every use_ticks it gets
 *   repair_steps of Tudur's Vehicle Mod's own repair step (2% of max health each).
 * defuse_kit: hold use while looking at a placed mine within range, for as long as the mine's own
 *   defuse_ticks: the mine comes back as an item.
 * detonator: use to set off every remote charge you placed within range.
 * mine_detector: while held, placed mines within range are outlined and it beeps faster the closer
 *   the nearest one is.
 * laser_designator: hold use to put a laser spot on what you're pointing at (within range). The spot
 *   can be locked onto by missiles - handheld or vehicle - aimed at it, so a target that isn't a
 *   creature or vehicle (a building, a position) can still be locked. Everyone can see the spot.
 *
 * uses: durability (each heal/repair step/defuse/detonation uses one); 0 = unlimited.
 * max_stack: how many stack together (only when uses is 0). */
public record EquipmentDefinition(
		Type type,
		Optional<String> displayName,
		Optional<Identifier> model,
		Optional<Identifier> texture,
		Map<ItemDisplayContext, HandheldDefinition.DisplayTransform> display,
		int maxStack,
		int uses,
		int useTicks,
		Optional<Float> range,
		Optional<AttachmentDefinition.Zoom> zoom,
		boolean rangefinder,
		float heal,
		int regenerationTicks,
		int repairSteps,
		Optional<String> sound,
		HeldMovement movement
) {

	public static final Codec<EquipmentDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Type.CODEC.fieldOf("type").forGetter(EquipmentDefinition::type),
			Codec.STRING.optionalFieldOf("display_name").forGetter(EquipmentDefinition::displayName),
			Identifier.CODEC.optionalFieldOf("model").forGetter(EquipmentDefinition::model),
			Identifier.CODEC.optionalFieldOf("texture").forGetter(EquipmentDefinition::texture),
			DefinitionCodecs.DISPLAY.forGetter(EquipmentDefinition::display),
			Codec.intRange(1, 99).optionalFieldOf("max_stack", 1).forGetter(EquipmentDefinition::maxStack),
			Codec.intRange(0, 100000).optionalFieldOf("uses", 0).forGetter(EquipmentDefinition::uses),
			Codec.intRange(1, 72000).optionalFieldOf("use_ticks", 40).forGetter(EquipmentDefinition::useTicks),
			Codec.floatRange(0f, 1024f).optionalFieldOf("range").forGetter(EquipmentDefinition::range),
			AttachmentDefinition.Zoom.CODEC.optionalFieldOf("zoom").forGetter(EquipmentDefinition::zoom),
			Codec.BOOL.optionalFieldOf("rangefinder", false).forGetter(EquipmentDefinition::rangefinder),
			Codec.floatRange(0f, 1000f).optionalFieldOf("heal", 8f).forGetter(EquipmentDefinition::heal),
			Codec.intRange(0, 72000).optionalFieldOf("regeneration_ticks", 0).forGetter(EquipmentDefinition::regenerationTicks),
			Codec.intRange(1, 50).optionalFieldOf("repair_steps", 1).forGetter(EquipmentDefinition::repairSteps),
			Codec.STRING.optionalFieldOf("sound").forGetter(EquipmentDefinition::sound),
			HeldMovement.MAP_CODEC.forGetter(EquipmentDefinition::movement)
	).apply(instance, EquipmentDefinition::new));

	/** range, or the type's own default when not set. */
	public float effectiveRange() {
		return this.range.orElseGet(() -> switch (this.type) {
			case FIRST_AID, DEFUSE_KIT -> 3f;
			case REPAIR_KIT -> 5f;
			case MINE_DETECTOR -> 12f;
			case DETONATOR -> 256f;
			case LASER_DESIGNATOR -> 512f;
			case BINOCULARS -> 512f;
		});
	}

	/** Whether it's used by holding the use key (rather than a single press, or not at all). */
	public boolean isHeld() {
		return switch (this.type) {
			case BINOCULARS, FIRST_AID, REPAIR_KIT, DEFUSE_KIT, LASER_DESIGNATOR -> true;
			case DETONATOR, MINE_DETECTOR -> false;
		};
	}

	public enum Type {
		BINOCULARS, FIRST_AID, REPAIR_KIT, DEFUSE_KIT, DETONATOR, MINE_DETECTOR, LASER_DESIGNATOR;

		public static final Codec<Type> CODEC = Codec.STRING.comapFlatMap(
				s -> {
					try {
						return com.mojang.serialization.DataResult.success(Type.valueOf(s.toUpperCase(Locale.ROOT)));
					} catch (IllegalArgumentException e) {
						return com.mojang.serialization.DataResult.error(() -> "Unknown equipment type: " + s);
					}
				},
				type -> type.name().toLowerCase(Locale.ROOT));
	}
}
