package com.example.tudursguns.handheld;

import com.example.tudursvehiclemod.asset.WeaponStatsLoader;
import net.minecraft.util.Identifier;

import java.util.List;

/** The definition kinds added after handheld weapons/attachments/throwables, which all share one
 * loader and one sync payload (DefinitionSet). */
public final class ModDefinitions {

	private ModDefinitions() {
	}

	/** data/<namespace>/mine/*.json - named after display_name, else its weapon file's DisplayName. */
	public static final DefinitionSet<MineDefinition> MINES = new DefinitionSet<>("mine", MineDefinition.CODEC,
			(id, def) -> def.displayName().orElseGet(() -> WeaponStatsLoader.get(def.weapon()).displayName()));

	/** data/<namespace>/armor/*.json */
	public static final DefinitionSet<ArmorDefinition> ARMOR = new DefinitionSet<>("armor", ArmorDefinition.CODEC,
			(id, def) -> def.displayName().orElse(id.getPath()));

	/** data/<namespace>/equipment/*.json */
	public static final DefinitionSet<EquipmentDefinition> EQUIPMENT = new DefinitionSet<>("equipment", EquipmentDefinition.CODEC,
			(id, def) -> def.displayName().orElse(id.getPath()));

	/** data/<namespace>/ammo/*.json */
	public static final DefinitionSet<AmmoDefinition> AMMO = new DefinitionSet<>("ammo", AmmoDefinition.CODEC,
			(id, def) -> def.displayName().orElse(id.getPath()));

	/** data/<namespace>/gun_recipe/*.json - the gun crafting table's recipes. */
	public static final DefinitionSet<GunRecipeDefinition> GUN_RECIPES = new DefinitionSet<>("gun_recipe", GunRecipeDefinition.CODEC,
			(id, def) -> id.toString());

	public static final List<DefinitionSet<?>> ALL = List.of(MINES, ARMOR, EQUIPMENT, AMMO, GUN_RECIPES);

	public static DefinitionSet<?> byKind(String kind) {
		for (DefinitionSet<?> set : ALL) {
			if (set.kind().equals(kind)) {
				return set;
			}
		}
		return null;
	}
}
