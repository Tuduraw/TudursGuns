package com.example.tudursguns.registry;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.handheld.HandheldDefinitions;
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
import java.util.Map;

public final class ModItemGroups {

	private ModItemGroups() {
	}

	public static final RegistryKey<ItemGroup> HANDHELD =
			RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(TudursGuns.MOD_ID, "handheld"));

	public static void register() {
		Registry.register(Registries.ITEM_GROUP, HANDHELD, FabricItemGroup.builder()
				.icon(() -> new ItemStack(ModItems.HANDHELD_WEAPON))
				.displayName(Text.translatable("itemGroup.tudursguns.handheld"))
				// One entry per definition the client has received from the server, loaded and ready
				// to fire. Collected when the creative inventory is built, which is client-side only.
				.entries((displayContext, entries) -> {
					List<Map.Entry<Identifier, HandheldDefinitions.ClientEntry>> sorted =
							new ArrayList<>(HandheldDefinitions.client().entrySet());
					sorted.sort(Comparator.comparing(entry -> entry.getKey().toString()));
					for (Map.Entry<Identifier, HandheldDefinitions.ClientEntry> entry : sorted) {
						entries.add(createStack(entry.getKey(), entry.getValue().weapon().magazineSize()));
					}
				})
				.build());
	}

	/** A weapon stack for the given definition with a full magazine. */
	public static ItemStack createStack(Identifier weaponId, int magazineSize) {
		ItemStack stack = new ItemStack(ModItems.HANDHELD_WEAPON);
		stack.set(ModComponents.WEAPON, weaponId);
		if (magazineSize > 0) {
			stack.set(ModComponents.AMMO, magazineSize);
		}
		return stack;
	}
}
