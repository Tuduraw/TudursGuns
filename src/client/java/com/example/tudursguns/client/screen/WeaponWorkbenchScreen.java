package com.example.tudursguns.client.screen;

import com.example.tudursguns.screen.WeaponWorkbenchScreenHandler;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Weapon slot on the left, one row per attachment slot of the weapon on the right (labelled with
 * the slot name), player inventory below. Drawn with plain rectangles - no texture needed. */
public class WeaponWorkbenchScreen extends AbstractContainerScreen<WeaponWorkbenchScreenHandler> {

	private static final int PANEL_COLOR = 0xFFC6C6C6;
	private static final int SLOT_FILL = 0xFF8B8B8B;
	private static final int SLOT_SHADOW = 0xFF373737;
	private static final int SLOT_HIGHLIGHT = 0xFFFFFFFF;
	private static final int TEXT_COLOR = 0xFF404040;

	public WeaponWorkbenchScreen(WeaponWorkbenchScreenHandler handler, Inventory inventory, Component title) {
		super(handler, inventory, title);
		this.imageWidth = 176;
		this.imageHeight = 184;
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
		int x = this.leftPos;
		int y = this.topPos;
		context.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL_COLOR);

		for (Slot slot : this.menu.slots) {
			if (slot.isActive()) {
				drawSlot(context, x + slot.x, y + slot.y);
			}
		}
		List<String> names = this.menu.slotNames();
		for (int i = 0; i < WeaponWorkbenchScreenHandler.MAX_ATTACHMENT_SLOTS && i < names.size(); i++) {
			context.drawString(this.font, slotLabel(names.get(i)), x + WeaponWorkbenchScreenHandler.ATTACHMENT_SLOT_X + 20,
					y + WeaponWorkbenchScreenHandler.ATTACHMENT_SLOT_Y + i * WeaponWorkbenchScreenHandler.ATTACHMENT_SLOT_SPACING + 4,
					TEXT_COLOR, false);
		}
		if (this.menu.weapon().isEmpty()) {
			context.drawString(this.font, Component.translatable("gui.tudursguns.weapon_workbench.insert_weapon"),
					x + WeaponWorkbenchScreenHandler.ATTACHMENT_SLOT_X, y + 39, TEXT_COLOR, false);
		} else if (names.isEmpty()) {
			context.drawString(this.font, Component.translatable("gui.tudursguns.weapon_workbench.no_slots"),
					x + WeaponWorkbenchScreenHandler.ATTACHMENT_SLOT_X, y + 39, TEXT_COLOR, false);
		}
	}

	/** gui.tudursguns.slot.<name> if the language file has it, else the raw slot name. */
	private static Component slotLabel(String slotName) {
		return Component.translatableWithFallback("gui.tudursguns.slot." + slotName, slotName);
	}

	/** One 18x18 recessed slot around a Slot's own coordinate. */
	private static void drawSlot(GuiGraphics context, int slotX, int slotY) {
		int x = slotX - 1;
		int y = slotY - 1;
		context.fill(x, y, x + 18, y + 18, SLOT_FILL);
		context.fill(x, y, x + 18, y + 1, SLOT_SHADOW);
		context.fill(x, y, x + 1, y + 18, SLOT_SHADOW);
		context.fill(x, y + 17, x + 18, y + 18, SLOT_HIGHLIGHT);
		context.fill(x + 17, y, x + 18, y + 18, SLOT_HIGHLIGHT);
	}
}
