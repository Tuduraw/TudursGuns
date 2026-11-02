package com.example.tudursguns.registry;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.handheld.DefinitionSet;
import com.example.tudursguns.handheld.ModDefinitions;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public final class ModItemGroups {

	private ModItemGroups() {
	}

	public static final ResourceKey<CreativeModeTab> HANDHELD =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(TudursGuns.MOD_ID, "handheld"));

	public static void register() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, HANDHELD, FabricItemGroup.builder()
				.icon(() -> new ItemStack(ModItems.HANDHELD_WEAPON))
				.title(Component.translatable("itemGroup.tudursguns.handheld"))
				// One entry per definition the client has received from the server (weapons loaded).
				// Collected when the creative inventory is built, which is client-side only.
				.displayItems((displayContext, entries) -> {
					addAll(entries, ModDefinitions.HANDHELD, ModItems::weaponStack);
					addAll(entries, ModDefinitions.ATTACHMENTS, ModItems::attachmentStack);
					addAll(entries, ModDefinitions.THROWABLES, ModItems::throwableStack);
					addAll(entries, ModDefinitions.MINES, ModItems::mineStack);
					addAll(entries, ModDefinitions.ARMOR, ModItems::armorStack);
					addAll(entries, ModDefinitions.EQUIPMENT, ModItems::equipmentStack);
					addAll(entries, ModDefinitions.AMMO, ModItems::ammoStack);
					entries.accept(ModBlocks.WEAPON_WORKBENCH_ITEM);
					entries.accept(ModBlocks.GUN_CRAFTING_TABLE_ITEM);
					entries.accept(ModBlocks.AMMO_BOX_ITEM);
					entries.accept(ModBlocks.SOLDIER_POST_ITEM);
					entries.accept(ModEntityTypes.ENEMY_SOLDIER_SPAWN_EGG);
				})
				.build());
	}

	private static void addAll(CreativeModeTab.Output entries, DefinitionSet<?> set, Function<Identifier, ItemStack> stack) {
		List<Identifier> ids = new ArrayList<>(set.client().keySet());
		ids.sort(Comparator.comparing(Identifier::toString));
		for (Identifier id : ids) {
			entries.accept(stack.apply(id));
		}
	}
}
