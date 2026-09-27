package com.example.tudursguns.item;

import com.example.tudursguns.handheld.AmmoDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.registry.ModComponents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.Objects;
import java.util.function.Consumer;

/** The one item every ammo definition (magazine, clip, shell box) is; which one comes from its
 * tudursguns:ammo_type component. Weapons naming it reload from it. */
public class AmmoItem extends Item {

	public AmmoItem(Settings settings) {
		super(settings);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot) {
		AmmoDefinition def = ModDefinitions.AMMO.getServer(stack.get(ModComponents.AMMO_TYPE));
		if (def != null && !Objects.equals(stack.get(DataComponentTypes.MAX_STACK_SIZE), def.maxStack())) {
			stack.set(DataComponentTypes.MAX_STACK_SIZE, def.maxStack());
		}
	}

	@Override
	public Text getName(ItemStack stack) {
		String name = ModDefinitions.AMMO.name(stack.get(ModComponents.AMMO_TYPE));
		return name != null ? Text.literal(name) : super.getName(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, TooltipDisplayComponent displayComponent,
			Consumer<Text> textConsumer, TooltipType type) {
		Identifier id = stack.get(ModComponents.AMMO_TYPE);
		AmmoDefinition def = ModDefinitions.AMMO.getAny(id);
		if (def == null) {
			return;
		}
		textConsumer.accept(Text.translatable("tooltip.tudursguns.ammo_rounds", def.rounds()).formatted(Formatting.GRAY));
		if (type.isAdvanced()) {
			textConsumer.accept(Text.literal(id.toString()).formatted(Formatting.DARK_GRAY));
		}
	}
}
