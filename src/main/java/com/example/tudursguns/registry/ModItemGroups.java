package com.example.tudursguns.registry;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.handheld.DefinitionSet;
import com.example.tudursguns.handheld.ModDefinitions;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public final class ModItemGroups {

	private ModItemGroups() {
	}

	public static final RegistryKey<ItemGroup> HANDHELD =
			RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(TudursGuns.MOD_ID, "handheld"));

	public static void register() {
		Registry.register(Registries.ITEM_GROUP, HANDHELD, FabricItemGroup.builder()
				.icon(() -> new ItemStack(ModItems.HANDHELD_WEAPON))
				.displayName(Text.translatable("itemGroup.tudursguns.handheld"))
				// One entry per definition the client has received from the server (weapons loaded).
				// Collected when the creative inventory is built, which is client-side only.
				.entries((displayContext, entries) -> {
					addAll(entries, ModDefinitions.HANDHELD, ModItems::weaponStack);
					addAll(entries, ModDefinitions.ATTACHMENTS, ModItems::attachmentStack);
					addAll(entries, ModDefinitions.THROWABLES, ModItems::throwableStack);
					addAll(entries, ModDefinitions.MINES, ModItems::mineStack);
					addAll(entries, ModDefinitions.ARMOR, ModItems::armorStack);
					addAll(entries, ModDefinitions.EQUIPMENT, ModItems::equipmentStack);
					addAll(entries, ModDefinitions.AMMO, ModItems::ammoStack);
					entries.add(ModBlocks.WEAPON_WORKBENCH_ITEM);
					entries.add(ModBlocks.GUN_CRAFTING_TABLE_ITEM);
					entries.add(ModBlocks.AMMO_BOX_ITEM);
					entries.add(ModBlocks.SOLDIER_POST_ITEM);
					entries.add(ModEntityTypes.ENEMY_SOLDIER_SPAWN_EGG);
				})
				.build());
	}

	private static void addAll(ItemGroup.Entries entries, DefinitionSet<?> set, Function<Identifier, ItemStack> stack) {
		List<Identifier> ids = new ArrayList<>(set.client().keySet());
		ids.sort(Comparator.comparing(Identifier::toString));
		for (Identifier id : ids) {
			entries.add(stack.apply(id));
		}
	}
}
