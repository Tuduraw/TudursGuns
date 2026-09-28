package com.example.tudursguns.soldier;

import com.example.tudursguns.TudursGunsConfig;
import com.example.tudursguns.handheld.ArmorDefinition;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.registry.ModItems;
import com.example.tudursguns.weapon.HandheldCombat;
import com.example.tudursvehiclemod.asset.WeaponStatsLoader;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Kits out an enemy soldier from the definitions' soldier_weight: one weapon, picked with odds in
 * proportion to the weights, and for each armor slot one piece or nothing (the server config's
 * enemy_soldier_no_armor_weight). Rare, strong gear is simply given a low weight. */
public final class EnemySoldierGear {

	private EnemySoldierGear() {
	}

	private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private record Weighted(Identifier id, int weight) {
	}

	public static void equip(MobEntity soldier, Random random) {
		TudursGunsConfig.Data config = TudursGunsConfig.get();
		List<Weighted> weapons = new ArrayList<>();
		for (Map.Entry<Identifier, HandheldDefinition> entry : ModDefinitions.HANDHELD.server().entrySet()) {
			int weight = entry.getValue().handling().soldierWeight();
			if (weight > 0 && HandheldCombat.isSupported(WeaponStatsLoader.get(entry.getValue().weapon()).weaponType())) {
				weapons.add(new Weighted(entry.getKey(), weight));
			}
		}
		Identifier weapon = pick(weapons, 0, random);
		if (weapon != null) {
			soldier.equipStack(EquipmentSlot.MAINHAND, ModItems.weaponStack(weapon));
		}
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			List<Weighted> pieces = new ArrayList<>();
			for (Map.Entry<Identifier, ArmorDefinition> entry : ModDefinitions.ARMOR.server().entrySet()) {
				if (entry.getValue().slot() == slot && entry.getValue().soldierWeight() > 0) {
					pieces.add(new Weighted(entry.getKey(), entry.getValue().soldierWeight()));
				}
			}
			Identifier piece = pick(pieces, config.enemy_soldier_no_armor_weight, random);
			if (piece != null) {
				soldier.equipStack(slot, ModItems.armorStack(piece));
			}
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			soldier.setEquipmentDropChance(slot, config.enemy_soldier_drop_chance);
		}
	}

	/** One of candidates, in proportion to its weight - or null with odds of noneWeight. */
	private static Identifier pick(List<Weighted> candidates, int noneWeight, Random random) {
		int total = noneWeight;
		for (Weighted candidate : candidates) {
			total += candidate.weight();
		}
		if (total <= 0) {
			return null;
		}
		int roll = random.nextInt(total);
		for (Weighted candidate : candidates) {
			roll -= candidate.weight();
			if (roll < 0) {
				return candidate.id();
			}
		}
		return null;
	}
}
