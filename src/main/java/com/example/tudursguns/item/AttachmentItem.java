package com.example.tudursguns.item;

import com.example.tudursguns.handheld.AttachmentDefinition;
import com.example.tudursguns.handheld.HandheldDefinitions;
import com.example.tudursguns.registry.ModComponents;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.function.Consumer;

/** The one item every weapon attachment is; which one comes from its tudursguns:attachment
 * component (an AttachmentDefinition id). Fitted and removed at the weapon workbench. */
public class AttachmentItem extends Item {

	public AttachmentItem(Settings settings) {
		super(settings);
	}

	@Override
	public Text getName(ItemStack stack) {
		AttachmentDefinition def = HandheldDefinitions.getAnyAttachment(stack.get(ModComponents.ATTACHMENT));
		return def != null ? Text.literal(def.displayName()) : super.getName(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, TooltipDisplayComponent displayComponent,
			Consumer<Text> textConsumer, TooltipType type) {
		Identifier id = stack.get(ModComponents.ATTACHMENT);
		AttachmentDefinition def = HandheldDefinitions.getAnyAttachment(id);
		if (def == null) {
			return;
		}
		def.movement().appendTooltip(textConsumer);
		def.zoom().ifPresent(zoom -> textConsumer.accept(Text.translatable("tooltip.tudursguns.attachment.zoom",
				format(zoom.min()), format(zoom.max())).formatted(Formatting.BLUE)));
		if (def.magazineSizeMultiplier() != 1f) {
			textConsumer.accept(Text.translatable("tooltip.tudursguns.attachment.magazine",
					format(def.magazineSizeMultiplier())).formatted(Formatting.BLUE));
		}
		if (def.magazineSizeBonus() != 0) {
			textConsumer.accept(Text.translatable("tooltip.tudursguns.attachment.magazine_bonus",
					def.magazineSizeBonus()).formatted(Formatting.BLUE));
		}
		if (def.reloadTimeMultiplier() != 1f) {
			textConsumer.accept(Text.translatable("tooltip.tudursguns.attachment.reload",
					format(def.reloadTimeMultiplier())).formatted(Formatting.BLUE));
		}
		if (def.soundOverride().isPresent() || def.soundVolumeMultiplier() < 1f) {
			textConsumer.accept(Text.translatable("tooltip.tudursguns.attachment.suppressor").formatted(Formatting.BLUE));
		}
		if (def.accuracyMultiplier() != 1f) {
			textConsumer.accept(Text.translatable("tooltip.tudursguns.attachment.accuracy",
					format(def.accuracyMultiplier())).formatted(Formatting.BLUE));
		}
		if (def.meleeDamageBonus() != 0f) {
			textConsumer.accept(Text.translatable("tooltip.tudursguns.attachment.melee",
					format(def.meleeDamageBonus())).formatted(Formatting.BLUE));
		}
		if (type.isAdvanced()) {
			textConsumer.accept(Text.literal(id.toString()).formatted(Formatting.DARK_GRAY));
		}
	}

	private static String format(float value) {
		return value == Math.rint(value) ? Integer.toString((int) value) : String.format(java.util.Locale.ROOT, "%.2f", value);
	}
}
