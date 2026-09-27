package com.example.tudursguns.item;

import com.example.tudursguns.handheld.AttachmentDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.registry.ModComponents;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Consumer;

/** The one item every weapon attachment is; which one comes from its tudursguns:attachment
 * component (an AttachmentDefinition id). Fitted and removed at the weapon workbench. */
public class AttachmentItem extends DefinedItem<AttachmentDefinition> {

	public AttachmentItem(Settings settings) {
		super(settings, ModDefinitions.ATTACHMENTS, ModComponents.ATTACHMENT);
	}

	@Override
	protected void appendDetails(ItemStack stack, AttachmentDefinition def, Consumer<Text> textConsumer) {
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
	}

	private static String format(float value) {
		return value == Math.rint(value) ? Integer.toString((int) value) : String.format(java.util.Locale.ROOT, "%.2f", value);
	}
}
