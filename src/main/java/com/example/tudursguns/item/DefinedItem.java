package com.example.tudursguns.item;

import com.example.tudursguns.handheld.DefinitionSet;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** An item type whose stacks each name a definition through a component (see ModItems): the stack
 * is named after its definition, and its tooltip describes it. */
public abstract class DefinedItem<T> extends Item {

	private final DefinitionSet<T> definitions;
	private final DataComponentType<Identifier> component;

	protected DefinedItem(Properties settings, DefinitionSet<T> definitions, DataComponentType<Identifier> component) {
		super(settings);
		this.definitions = definitions;
		this.component = component;
	}

	@Override
	public Component getName(ItemStack stack) {
		String name = this.definitions.name(stack.get(this.component));
		return name != null ? Component.literal(name) : super.getName(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay displayComponent,
			Consumer<Component> textConsumer, TooltipFlag type) {
		Identifier id = stack.get(this.component);
		T def = this.definitions.getAny(id);
		if (def == null) {
			return;
		}
		appendDetails(stack, def, textConsumer);
		if (type.isAdvanced()) {
			textConsumer.accept(Component.literal(id.toString()).withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	/** Sets a component only when it differs - item components filled in from a definition are
	 * re-applied every inventory tick, and an unchanged stack shouldn't be marked dirty. */
	public static <C> void setIfChanged(ItemStack stack, DataComponentType<C> type, C value) {
		if (!Objects.equals(stack.get(type), value)) {
			stack.set(type, value);
		}
	}

	/** The tooltip lines describing the definition. */
	protected abstract void appendDetails(ItemStack stack, T def, Consumer<Component> tooltip);
}
