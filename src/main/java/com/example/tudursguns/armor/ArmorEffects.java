package com.example.tudursguns.armor;

import com.example.tudursguns.TudursGunsConfig;
import com.example.tudursguns.handheld.ArmorDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.item.ArmorItem;
import com.example.tudursguns.item.DefinedItem;
import com.example.tudursguns.registry.ModComponents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.EquipmentAssetKeys;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Everything armor does beyond vanilla's armor points: the components its stack needs, protection
 * against bullets/blasts/headshots, night vision, gas masks and camouflage. */
public final class ArmorEffects {

	private ArmorEffects() {
	}

	public static final List<EquipmentSlot> ARMOR_SLOTS =
			List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

	/** The definition of the armor worn in slot, or null. */
	public static ArmorDefinition worn(LivingEntity entity, EquipmentSlot slot) {
		ItemStack stack = entity.getEquippedStack(slot);
		if (!(stack.getItem() instanceof ArmorItem)) {
			return null;
		}
		ArmorDefinition def = ModDefinitions.ARMOR.getAny(stack.get(ModComponents.ARMOR));
		return def != null && def.slot() == slot ? def : null;
	}

	// ---------------------------------------------------------------- stack components

	/** Brings the stack's vanilla components (equippable, attributes, durability, item model) in line
	 * with its definition. Called every inventory tick on the server, so a stack from a recipe, a
	 * command or an older definition picks up the current values; unchanged components aren't touched. */
	public static void applyComponents(ItemStack stack, ArmorDefinition def) {
		Identifier id = stack.get(ModComponents.ARMOR);
		EquippableComponent.Builder equippable = EquippableComponent.builder(def.slot());
		def.equipmentAsset().ifPresent(asset -> equippable.model(RegistryKey.of(EquipmentAssetKeys.REGISTRY_KEY, asset)));
		Registries.SOUND_EVENT.getEntry(Identifier.ofVanilla("item.armor.equip_iron")).ifPresent(equippable::equipSound);
		DefinedItem.setIfChanged(stack, DataComponentTypes.EQUIPPABLE, equippable.build());

		AttributeModifierSlot slot = AttributeModifierSlot.forEquipmentSlot(def.slot());
		Identifier modifierId = Identifier.of(id.getNamespace(), "armor." + def.slot().getName());
		AttributeModifiersComponent.Builder attributes = AttributeModifiersComponent.builder();
		if (def.armor() > 0f) {
			attributes.add(EntityAttributes.ARMOR, new EntityAttributeModifier(modifierId, def.armor(), EntityAttributeModifier.Operation.ADD_VALUE), slot);
		}
		if (def.toughness() > 0f) {
			attributes.add(EntityAttributes.ARMOR_TOUGHNESS, new EntityAttributeModifier(modifierId, def.toughness(), EntityAttributeModifier.Operation.ADD_VALUE), slot);
		}
		if (def.knockbackResistance() > 0f) {
			attributes.add(EntityAttributes.KNOCKBACK_RESISTANCE, new EntityAttributeModifier(modifierId, def.knockbackResistance(), EntityAttributeModifier.Operation.ADD_VALUE), slot);
		}
		if (def.movementSpeed() != 0f) {
			attributes.add(EntityAttributes.MOVEMENT_SPEED, new EntityAttributeModifier(modifierId, def.movementSpeed(), EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), slot);
		}
		DefinedItem.setIfChanged(stack, DataComponentTypes.ATTRIBUTE_MODIFIERS, attributes.build());

		if (def.durability() > 0) {
			DefinedItem.setIfChanged(stack, DataComponentTypes.MAX_DAMAGE, def.durability());
			if (!stack.contains(DataComponentTypes.DAMAGE)) {
				stack.set(DataComponentTypes.DAMAGE, 0);
			}
		} else {
			stack.remove(DataComponentTypes.MAX_DAMAGE);
			stack.remove(DataComponentTypes.DAMAGE);
		}
		if (def.itemModel().isPresent()) {
			DefinedItem.setIfChanged(stack, DataComponentTypes.ITEM_MODEL, def.itemModel().get());
		}
	}

	// ---------------------------------------------------------------- damage

	/** Applied to the final damage (after vanilla armor and enchantments): headshot bonus for a
	 * projectile hitting the head, then the worn armor's ballistic/blast protection. Ballistic hits
	 * also wear the plates that stopped them. */
	public static float modifyDamage(LivingEntity target, DamageSource source, float amount) {
		if (amount <= 0f) {
			return amount;
		}
		boolean explosion = source.isIn(DamageTypeTags.IS_EXPLOSION);
		boolean projectile = !explosion && source.isIn(DamageTypeTags.IS_PROJECTILE) && source.getSource() != null;
		if (!explosion && !projectile) {
			return amount;
		}
		float result = amount;
		if (projectile && isHeadshot(target, source.getSource())) {
			ArmorDefinition helmet = worn(target, EquipmentSlot.HEAD);
			float headshotProtection = helmet == null ? 0f : helmet.protection().headshot();
			result *= 1f + (TudursGunsConfig.headshotMultiplier() - 1f) * (1f - headshotProtection);
		}
		float passed = 1f;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ArmorDefinition def = worn(target, slot);
			if (def == null) {
				continue;
			}
			float protection = explosion ? def.protection().blast() : def.protection().ballistic();
			if (protection <= 0f) {
				continue;
			}
			passed *= 1f - protection;
			if (projectile && def.durability() > 0) {
				// A plate that stops a bullet takes the hit: much more wear than vanilla's amount / 4.
				target.getEquippedStack(slot).damage(Math.max(1, Math.round(amount)), target, slot);
			}
		}
		return result * passed;
	}

	/** Where the projectile's path meets the target's box - above ~78% of its height is the head. */
	private static boolean isHeadshot(LivingEntity target, Entity projectile) {
		if (TudursGunsConfig.headshotMultiplier() <= 1f || (TudursGunsConfig.headshotsPlayersOnly() && !(target instanceof PlayerEntity))) {
			return false;
		}
		Box box = target.getBoundingBox();
		Vec3d start = projectile.getEntityPos();
		Vec3d velocity = projectile.getVelocity();
		Vec3d hit = box.contains(start) ? start
				: box.raycast(start.subtract(velocity), start.add(velocity.multiply(2.0))).orElse(start);
		return hit.y >= box.minY + box.getLengthY() * 0.78;
	}

	// ---------------------------------------------------------------- effects

	private static boolean anyWorn(LivingEntity entity, Predicate<ArmorDefinition> test) {
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ArmorDefinition def = worn(entity, slot);
			if (def != null && test.test(def)) {
				return true;
			}
		}
		return false;
	}

	public static boolean hasGasProtection(LivingEntity entity) {
		return anyWorn(entity, def -> def.effects().gasProtection());
	}

	public static boolean hasNightVision(LivingEntity entity) {
		return anyWorn(entity, def -> def.effects().nightVision());
	}

	/** Product of the worn armor's detection multipliers (1 = normal). */
	public static double detectionMultiplier(LivingEntity entity) {
		double multiplier = 1.0;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ArmorDefinition def = worn(entity, slot);
			if (def != null) {
				multiplier *= def.effects().detectionMultiplier();
			}
		}
		return multiplier;
	}

	/** Players whose night vision comes from their goggles (so taking them off ends it). */
	private static final Set<UUID> NIGHT_VISION = ConcurrentHashMap.newKeySet();
	private static final int NIGHT_VISION_TICKS = 400;

	/** Once a second per world: keeps goggle night vision topped up (well above the 10 s where the
	 * screen starts to flicker), and takes it away when the goggles come off. */
	public static void tickWorld(ServerWorld world) {
		if (world.getTime() % 20 != 0) {
			return;
		}
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (hasNightVision(player)) {
				NIGHT_VISION.add(player.getUuid());
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0, true, false, false));
			} else if (NIGHT_VISION.remove(player.getUuid())) {
				StatusEffectInstance current = player.getStatusEffect(StatusEffects.NIGHT_VISION);
				if (current != null && current.isAmbient() && current.getDuration() <= NIGHT_VISION_TICKS) {
					player.removeStatusEffect(StatusEffects.NIGHT_VISION);
				}
			}
		}
	}

	public static void forget(UUID playerId) {
		NIGHT_VISION.remove(playerId);
	}
}
