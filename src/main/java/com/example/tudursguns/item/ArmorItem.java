package com.example.tudursguns.item;

import com.example.tudursguns.armor.ArmorEffects;
import com.example.tudursguns.handheld.ArmorDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.registry.ModComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** The one item every piece of armor is; which one comes from its tudursguns:armor component. It is
 * worn through vanilla's own equippable component, which (with its attributes, durability and item
 * model) is filled in from the definition - see ArmorEffects.applyComponents. */
public class ArmorItem extends DefinedItem<ArmorDefinition> {

	public ArmorItem(Properties settings) {
		super(settings, ModDefinitions.ARMOR, ModComponents.ARMOR);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel world, Entity entity, EquipmentSlot slot) {
		ArmorDefinition def = ModDefinitions.ARMOR.getServer(stack.get(ModComponents.ARMOR));
		if (def != null) {
			ArmorEffects.applyComponents(stack, def);
		}
	}

	@Override
	protected void appendDetails(ItemStack stack, ArmorDefinition def, Consumer<Component> textConsumer) {
		ArmorDefinition.Protection protection = def.protection();
		percent(textConsumer, "ballistic", protection.ballistic());
		percent(textConsumer, "blast", protection.blast());
		percent(textConsumer, "headshot", protection.headshot());
		if (def.effects().nightVision()) {
			textConsumer.accept(Component.translatable("tooltip.tudursguns.armor.night_vision").withStyle(ChatFormatting.BLUE));
		}
		if (def.effects().gasProtection()) {
			textConsumer.accept(Component.translatable("tooltip.tudursguns.armor.gas_protection").withStyle(ChatFormatting.BLUE));
		}
		if (def.effects().detectionMultiplier() < 1f) {
			textConsumer.accept(Component.translatable("tooltip.tudursguns.armor.camouflage",
					Math.round(100f * (1f - def.effects().detectionMultiplier()))).withStyle(ChatFormatting.BLUE));
		}
	}

	private static void percent(Consumer<Component> textConsumer, String key, float value) {
		if (value > 0f) {
			textConsumer.accept(Component.translatable("tooltip.tudursguns.armor." + key, Math.round(value * 100f)).withStyle(ChatFormatting.BLUE));
		}
	}
}
