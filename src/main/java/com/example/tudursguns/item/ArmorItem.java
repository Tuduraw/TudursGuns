package com.example.tudursguns.item;

import com.example.tudursguns.armor.ArmorEffects;
import com.example.tudursguns.handheld.ArmorDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.registry.ModComponents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Consumer;

/** The one item every piece of armor is; which one comes from its tudursguns:armor component. It is
 * worn through vanilla's own equippable component, which (with its attributes, durability and item
 * model) is filled in from the definition - see ArmorEffects.applyComponents. */
public class ArmorItem extends DefinedItem<ArmorDefinition> {

	public ArmorItem(Settings settings) {
		super(settings, ModDefinitions.ARMOR, ModComponents.ARMOR);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot) {
		ArmorDefinition def = ModDefinitions.ARMOR.getServer(stack.get(ModComponents.ARMOR));
		if (def != null) {
			ArmorEffects.applyComponents(stack, def);
		}
	}

	@Override
	protected void appendDetails(ItemStack stack, ArmorDefinition def, Consumer<Text> textConsumer) {
		ArmorDefinition.Protection protection = def.protection();
		percent(textConsumer, "ballistic", protection.ballistic());
		percent(textConsumer, "blast", protection.blast());
		percent(textConsumer, "headshot", protection.headshot());
		if (def.effects().nightVision()) {
			textConsumer.accept(Text.translatable("tooltip.tudursguns.armor.night_vision").formatted(Formatting.BLUE));
		}
		if (def.effects().gasProtection()) {
			textConsumer.accept(Text.translatable("tooltip.tudursguns.armor.gas_protection").formatted(Formatting.BLUE));
		}
		if (def.effects().detectionMultiplier() < 1f) {
			textConsumer.accept(Text.translatable("tooltip.tudursguns.armor.camouflage",
					Math.round(100f * (1f - def.effects().detectionMultiplier()))).formatted(Formatting.BLUE));
		}
	}

	private static void percent(Consumer<Text> textConsumer, String key, float value) {
		if (value > 0f) {
			textConsumer.accept(Text.translatable("tooltip.tudursguns.armor." + key, Math.round(value * 100f)).formatted(Formatting.BLUE));
		}
	}
}
