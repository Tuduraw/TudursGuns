package com.example.tudursguns.item;

import com.example.tudursguns.handheld.AmmoDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.registry.ModComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** The one item every ammo definition (magazine, clip, shell box) is; which one comes from its
 * tudursguns:ammo_type component. Weapons naming it reload from it. */
public class AmmoItem extends DefinedItem<AmmoDefinition> {

	public AmmoItem(Properties settings) {
		super(settings, ModDefinitions.AMMO, ModComponents.AMMO_TYPE);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel world, Entity entity, EquipmentSlot slot) {
		AmmoDefinition def = ModDefinitions.AMMO.getServer(stack.get(ModComponents.AMMO_TYPE));
		if (def != null) {
			setIfChanged(stack, DataComponents.MAX_STACK_SIZE, def.maxStack());
		}
	}

	@Override
	protected void appendDetails(ItemStack stack, AmmoDefinition def, Consumer<Component> textConsumer) {
		textConsumer.accept(Component.translatable("tooltip.tudursguns.ammo_rounds", def.rounds()).withStyle(ChatFormatting.GRAY));
	}
}
