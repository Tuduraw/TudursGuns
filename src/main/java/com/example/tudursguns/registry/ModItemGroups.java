package com.example.tudursguns.registry;

import com.example.tudursguns.TudursGuns;
import com.example.tudursguns.armor.ArmorEffects;
import com.example.tudursguns.handheld.ArmorDefinition;
import com.example.tudursguns.handheld.EquipmentDefinition;
import com.example.tudursguns.handheld.HandheldDefinitions;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.item.EquipmentItem;
import com.example.tudursguns.screen.WeaponWorkbenchScreenHandler;
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
					List<Identifier> attachmentIds = new ArrayList<>(HandheldDefinitions.clientAttachments().keySet());
					attachmentIds.sort(Comparator.comparing(Identifier::toString));
					for (Identifier attachmentId : attachmentIds) {
						entries.add(WeaponWorkbenchScreenHandler.createAttachmentStack(attachmentId));
					}
					List<Identifier> throwableIds = new ArrayList<>(HandheldDefinitions.clientThrowables().keySet());
					throwableIds.sort(Comparator.comparing(Identifier::toString));
					for (Identifier throwableId : throwableIds) {
						ItemStack throwable = new ItemStack(ModItems.THROWABLE);
						throwable.set(ModComponents.THROWABLE, throwableId);
						entries.add(throwable);
					}
					for (Identifier mineId : sortedIds(ModDefinitions.MINES.client().keySet())) {
						ItemStack mine = new ItemStack(ModItems.MINE);
						mine.set(ModComponents.MINE, mineId);
						entries.add(mine);
					}
					for (Identifier armorId : sortedIds(ModDefinitions.ARMOR.client().keySet())) {
						entries.add(createArmorStack(armorId));
					}
					for (Identifier equipmentId : sortedIds(ModDefinitions.EQUIPMENT.client().keySet())) {
						entries.add(createEquipmentStack(equipmentId));
					}
					entries.add(ModBlocks.WEAPON_WORKBENCH_ITEM);
					entries.add(ModBlocks.AMMO_BOX_ITEM);
				})
				.build());
	}

	private static List<Identifier> sortedIds(java.util.Collection<Identifier> ids) {
		List<Identifier> sorted = new ArrayList<>(ids);
		sorted.sort(Comparator.comparing(Identifier::toString));
		return sorted;
	}

	/** An armor stack with its components already filled in, so it can be worn straight away. */
	public static ItemStack createArmorStack(Identifier armorId) {
		ItemStack stack = new ItemStack(ModItems.ARMOR);
		stack.set(ModComponents.ARMOR, armorId);
		ArmorDefinition def = ModDefinitions.ARMOR.getAny(armorId);
		if (def != null) {
			ArmorEffects.applyComponents(stack, def);
		}
		return stack;
	}

	public static ItemStack createEquipmentStack(Identifier equipmentId) {
		ItemStack stack = new ItemStack(ModItems.EQUIPMENT);
		stack.set(ModComponents.EQUIPMENT, equipmentId);
		EquipmentDefinition def = ModDefinitions.EQUIPMENT.getAny(equipmentId);
		if (def != null) {
			EquipmentItem.applyComponents(stack, def);
		}
		return stack;
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
