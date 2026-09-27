package com.example.tudursguns.item;

import com.example.tudursguns.handheld.AmmoDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.registry.ModComponents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Objects;
import java.util.function.Consumer;

/** The one item every ammo definition (magazine, clip, shell box) is; which one comes from its
 * tudursguns:ammo_type component. Weapons naming it reload from it. */
public class AmmoItem extends DefinedItem<AmmoDefinition> {

	public AmmoItem(Settings settings) {
		super(settings, ModDefinitions.AMMO, ModComponents.AMMO_TYPE);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot) {
		AmmoDefinition def = ModDefinitions.AMMO.getServer(stack.get(ModComponents.AMMO_TYPE));
		if (def != null && !Objects.equals(stack.get(DataComponentTypes.MAX_STACK_SIZE), def.maxStack())) {
			stack.set(DataComponentTypes.MAX_STACK_SIZE, def.maxStack());
		}
	}

	@Override
	protected void appendDetails(ItemStack stack, AmmoDefinition def, Consumer<Text> textConsumer) {
		textConsumer.accept(Text.translatable("tooltip.tudursguns.ammo_rounds", def.rounds()).formatted(Formatting.GRAY));
	}
}
