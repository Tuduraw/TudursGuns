package com.example.tudursguns.item;

import com.example.tudursguns.handheld.DefinitionSet;
import net.minecraft.component.ComponentType;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.function.Consumer;

/** An item type whose stacks each name a definition through a component (see ModItems): the stack
 * is named after its definition, and its tooltip describes it. */
public abstract class DefinedItem<T> extends Item {

	private final DefinitionSet<T> definitions;
	private final ComponentType<Identifier> component;

	protected DefinedItem(Settings settings, DefinitionSet<T> definitions, ComponentType<Identifier> component) {
		super(settings);
		this.definitions = definitions;
		this.component = component;
	}

	@Override
	public Text getName(ItemStack stack) {
		String name = this.definitions.name(stack.get(this.component));
		return name != null ? Text.literal(name) : super.getName(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, TooltipDisplayComponent displayComponent,
			Consumer<Text> textConsumer, TooltipType type) {
		Identifier id = stack.get(this.component);
		T def = this.definitions.getAny(id);
		if (def == null) {
			return;
		}
		appendDetails(stack, def, textConsumer);
		if (type.isAdvanced()) {
			textConsumer.accept(Text.literal(id.toString()).formatted(Formatting.DARK_GRAY));
		}
	}

	/** The tooltip lines describing the definition. */
	protected abstract void appendDetails(ItemStack stack, T def, Consumer<Text> tooltip);
}
