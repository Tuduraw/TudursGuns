package com.example.tudursguns.armor;

import com.example.tudursguns.TudursGunsConfig;
import com.example.tudursguns.handheld.ArmorDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.item.ArmorItem;
import com.example.tudursguns.item.DefinedItem;
import com.example.tudursguns.registry.ModComponents;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Everything armor does beyond vanilla's armor points: the components its stack needs, protection
 * against bullets/blasts/headshots, night vision, gas masks and camouflage. */
public final class ArmorEffects {

	private ArmorEffects() {
	}

	public static final List<EquipmentSlot> ARMOR_SLOTS =
			List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

	/** assets/tudursguns/equipment/none.json: an equipment asset with no layers. */
	private static final Identifier NO_EQUIPMENT_ASSET = Identifier.fromNamespaceAndPath("tudursguns", "none");

	/** The definition of the armor worn in slot, or null. */
	public static ArmorDefinition worn(LivingEntity entity, EquipmentSlot slot) {
		ItemStack stack = entity.getItemBySlot(slot);
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
		Equippable.Builder equippable = Equippable.builder(def.slot());
		// Without a 2D look of its own, the empty asset: vanilla then draws nothing (not even a helmet
		// as an item on the head), leaving it to the OBJ (ObjArmorFeatureRenderer).
		equippable.setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, def.equipmentAsset().orElse(NO_EQUIPMENT_ASSET)));
		BuiltInRegistries.SOUND_EVENT.get(Identifier.withDefaultNamespace("item.armor.equip_iron")).ifPresent(equippable::setEquipSound);
		DefinedItem.setIfChanged(stack, DataComponents.EQUIPPABLE, equippable.build());

		EquipmentSlotGroup slot = EquipmentSlotGroup.bySlot(def.slot());
		Identifier modifierId = Identifier.fromNamespaceAndPath(id.getNamespace(), "armor." + def.slot().getName());
		ItemAttributeModifiers.Builder attributes = ItemAttributeModifiers.builder();
		if (def.armor() > 0f) {
			attributes.add(Attributes.ARMOR, new AttributeModifier(modifierId, def.armor(), AttributeModifier.Operation.ADD_VALUE), slot);
		}
		if (def.toughness() > 0f) {
			attributes.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(modifierId, def.toughness(), AttributeModifier.Operation.ADD_VALUE), slot);
		}
		if (def.knockbackResistance() > 0f) {
			attributes.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(modifierId, def.knockbackResistance(), AttributeModifier.Operation.ADD_VALUE), slot);
		}
		if (def.movementSpeed() != 0f) {
			attributes.add(Attributes.MOVEMENT_SPEED, new AttributeModifier(modifierId, def.movementSpeed(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), slot);
		}
		DefinedItem.setIfChanged(stack, DataComponents.ATTRIBUTE_MODIFIERS, attributes.build());

		if (def.durability() > 0) {
			DefinedItem.setIfChanged(stack, DataComponents.MAX_DAMAGE, def.durability());
			if (!stack.has(DataComponents.DAMAGE)) {
				stack.set(DataComponents.DAMAGE, 0);
			}
		} else {
			stack.remove(DataComponents.MAX_DAMAGE);
			stack.remove(DataComponents.DAMAGE);
		}
		if (def.itemModel().isPresent()) {
			DefinedItem.setIfChanged(stack, DataComponents.ITEM_MODEL, def.itemModel().get());
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
		boolean explosion = source.is(DamageTypeTags.IS_EXPLOSION);
		boolean projectile = !explosion && source.is(DamageTypeTags.IS_PROJECTILE) && source.getDirectEntity() != null;
		if (!explosion && !projectile) {
			return amount;
		}
		float result = amount;
		if (projectile && isHeadshot(target, source.getDirectEntity())) {
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
				target.getItemBySlot(slot).hurtAndBreak(Math.max(1, Math.round(amount)), target, slot);
			}
		}
		return result * passed;
	}

	/** Where the projectile's path meets the target's box - above ~78% of its height is the head. */
	private static boolean isHeadshot(LivingEntity target, Entity projectile) {
		if (TudursGunsConfig.headshotMultiplier() <= 1f || (TudursGunsConfig.headshotsPlayersOnly() && !(target instanceof Player))) {
			return false;
		}
		AABB box = target.getBoundingBox();
		Vec3 start = projectile.position();
		Vec3 velocity = projectile.getDeltaMovement();
		Vec3 hit = box.contains(start) ? start
				: box.clip(start.subtract(velocity), start.add(velocity.scale(2.0))).orElse(start);
		return hit.y >= box.minY + box.getYsize() * 0.78;
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
	public static void tickWorld(ServerLevel world) {
		if (world.getGameTime() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : world.players()) {
			if (hasNightVision(player)) {
				NIGHT_VISION.add(player.getUUID());
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0, true, false, false));
			} else if (NIGHT_VISION.remove(player.getUUID())) {
				MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
				if (current != null && current.isAmbient() && current.getDuration() <= NIGHT_VISION_TICKS) {
					player.removeEffect(MobEffects.NIGHT_VISION);
				}
			}
		}
	}

	public static void forget(UUID playerId) {
		NIGHT_VISION.remove(playerId);
	}
}
