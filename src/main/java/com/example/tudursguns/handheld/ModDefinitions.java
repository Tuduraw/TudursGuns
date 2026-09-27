package com.example.tudursguns.handheld;

import com.example.tudursvehiclemod.asset.WeaponStatsLoader;

import java.util.List;
import java.util.Optional;

/** Every data-driven definition kind (data/<namespace>/<kind>/*.json), each loaded and synced the
 * same way (DefinitionSet). */
public final class ModDefinitions {

	private ModDefinitions() {
	}

	/** display_name, else the weapon file's DisplayName. */
	private static String nameOrWeapon(Optional<String> displayName, String weapon) {
		return displayName.orElseGet(() -> WeaponStatsLoader.get(weapon).displayName());
	}

	public static final DefinitionSet<HandheldDefinition> HANDHELD = new DefinitionSet<>("handheld", HandheldDefinition.CODEC,
			(id, def) -> nameOrWeapon(def.displayName(), def.weapon()), HandheldDefinition::weapon);

	public static final DefinitionSet<AttachmentDefinition> ATTACHMENTS = new DefinitionSet<>("attachment", AttachmentDefinition.CODEC,
			(id, def) -> def.displayName());

	public static final DefinitionSet<ThrowableDefinition> THROWABLES = new DefinitionSet<>("throwable", ThrowableDefinition.CODEC,
			(id, def) -> nameOrWeapon(def.displayName(), def.weapon()), ThrowableDefinition::weapon);

	public static final DefinitionSet<MineDefinition> MINES = new DefinitionSet<>("mine", MineDefinition.CODEC,
			(id, def) -> nameOrWeapon(def.displayName(), def.weapon()));

	public static final DefinitionSet<ArmorDefinition> ARMOR = new DefinitionSet<>("armor", ArmorDefinition.CODEC,
			(id, def) -> def.displayName().orElse(id.getPath()));

	public static final DefinitionSet<EquipmentDefinition> EQUIPMENT = new DefinitionSet<>("equipment", EquipmentDefinition.CODEC,
			(id, def) -> def.displayName().orElse(id.getPath()));

	public static final DefinitionSet<AmmoDefinition> AMMO = new DefinitionSet<>("ammo", AmmoDefinition.CODEC,
			(id, def) -> def.displayName().orElse(id.getPath()));

	/** The gun crafting table's recipes. */
	public static final DefinitionSet<GunRecipeDefinition> GUN_RECIPES = new DefinitionSet<>("gun_recipe", GunRecipeDefinition.CODEC,
			(id, def) -> id.toString());

	public static final List<DefinitionSet<?>> ALL = List.of(HANDHELD, ATTACHMENTS, THROWABLES, MINES, ARMOR, EQUIPMENT, AMMO, GUN_RECIPES);

	public static DefinitionSet<?> byKind(String kind) {
		for (DefinitionSet<?> set : ALL) {
			if (set.kind().equals(kind)) {
				return set;
			}
		}
		return null;
	}
}
