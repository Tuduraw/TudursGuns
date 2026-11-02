package com.example.tudursguns.item;

import com.example.tudursguns.handheld.AttachmentDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.registry.ModComponents;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** The one item every weapon attachment is; which one comes from its tudursguns:attachment
 * component (an AttachmentDefinition id). Fitted and removed at the weapon workbench. */
public class AttachmentItem extends DefinedItem<AttachmentDefinition> {

	public AttachmentItem(Properties settings) {
		super(settings, ModDefinitions.ATTACHMENTS, ModComponents.ATTACHMENT);
	}

	@Override
	protected void appendDetails(ItemStack stack, AttachmentDefinition def, Consumer<Component> textConsumer) {
		def.movement().appendTooltip(textConsumer);
		def.zoom().ifPresent(zoom -> textConsumer.accept(Component.translatable("tooltip.tudursguns.attachment.zoom",
				format(zoom.min()), format(zoom.max())).withStyle(ChatFormatting.BLUE)));
		if (def.magazineSizeMultiplier() != 1f) {
			textConsumer.accept(Component.translatable("tooltip.tudursguns.attachment.magazine",
					format(def.magazineSizeMultiplier())).withStyle(ChatFormatting.BLUE));
		}
		if (def.magazineSizeBonus() != 0) {
			textConsumer.accept(Component.translatable("tooltip.tudursguns.attachment.magazine_bonus",
					def.magazineSizeBonus()).withStyle(ChatFormatting.BLUE));
		}
		if (def.reloadTimeMultiplier() != 1f) {
			textConsumer.accept(Component.translatable("tooltip.tudursguns.attachment.reload",
					format(def.reloadTimeMultiplier())).withStyle(ChatFormatting.BLUE));
		}
		if (def.soundOverride().isPresent() || def.soundVolumeMultiplier() < 1f) {
			textConsumer.accept(Component.translatable("tooltip.tudursguns.attachment.suppressor").withStyle(ChatFormatting.BLUE));
		}
		if (def.accuracyMultiplier() != 1f) {
			textConsumer.accept(Component.translatable("tooltip.tudursguns.attachment.accuracy",
					format(def.accuracyMultiplier())).withStyle(ChatFormatting.BLUE));
		}
		if (def.meleeDamageBonus() != 0f) {
			textConsumer.accept(Component.translatable("tooltip.tudursguns.attachment.melee",
					format(def.meleeDamageBonus())).withStyle(ChatFormatting.BLUE));
		}
	}

	private static String format(float value) {
		return value == Math.rint(value) ? Integer.toString((int) value) : String.format(Locale.ROOT, "%.2f", value);
	}
}
