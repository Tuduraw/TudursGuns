package com.example.tudursguns.client.screen;

import com.example.tudursguns.screen.WeaponWorkbenchScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

import java.util.List;

/** Weapon slot on the left, one row per attachment slot of the weapon on the right (labelled with
 * the slot name), player inventory below. Drawn with plain rectangles - no texture needed. */
public class WeaponWorkbenchScreen extends HandledScreen<WeaponWorkbenchScreenHandler> {

	private static final int PANEL_COLOR = 0xFFC6C6C6;
	private static final int SLOT_FILL = 0xFF8B8B8B;
	private static final int SLOT_SHADOW = 0xFF373737;
	private static final int SLOT_HIGHLIGHT = 0xFFFFFFFF;
	private static final int TEXT_COLOR = 0xFF404040;

	public WeaponWorkbenchScreen(WeaponWorkbenchScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = 184;
		this.playerInventoryTitleY = this.backgroundHeight - 94;
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int x = this.x;
		int y = this.y;
		context.fill(x, y, x + this.backgroundWidth, y + this.backgroundHeight, PANEL_COLOR);

		drawSlot(context, x + WeaponWorkbenchScreenHandler.WEAPON_SLOT_X, y + WeaponWorkbenchScreenHandler.WEAPON_SLOT_Y);
		List<String> names = this.handler.slotNames();
		for (int i = 0; i < WeaponWorkbenchScreenHandler.MAX_ATTACHMENT_SLOTS && i < names.size(); i++) {
			int slotX = x + WeaponWorkbenchScreenHandler.ATTACHMENT_SLOT_X;
			int slotY = y + WeaponWorkbenchScreenHandler.ATTACHMENT_SLOT_Y + i * WeaponWorkbenchScreenHandler.ATTACHMENT_SLOT_SPACING;
			drawSlot(context, slotX, slotY);
			context.drawText(this.textRenderer, slotLabel(names.get(i)), slotX + 20, slotY + 4, TEXT_COLOR, false);
		}
		if (this.handler.weapon().isEmpty()) {
			context.drawText(this.textRenderer, Text.translatable("gui.tudursguns.weapon_workbench.insert_weapon"),
					x + WeaponWorkbenchScreenHandler.ATTACHMENT_SLOT_X, y + 39, TEXT_COLOR, false);
		} else if (names.isEmpty()) {
			context.drawText(this.textRenderer, Text.translatable("gui.tudursguns.weapon_workbench.no_slots"),
					x + WeaponWorkbenchScreenHandler.ATTACHMENT_SLOT_X, y + 39, TEXT_COLOR, false);
		}

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				drawSlot(context, x + 8 + col * 18, y + 102 + row * 18);
			}
		}
		for (int col = 0; col < 9; col++) {
			drawSlot(context, x + 8 + col * 18, y + 160);
		}
	}

	/** gui.tudursguns.slot.<name> if the language file has it, else the raw slot name. */
	private static Text slotLabel(String slotName) {
		return Text.translatableWithFallback("gui.tudursguns.slot." + slotName, slotName);
	}

	/** One 18x18 recessed slot around a Slot's own coordinate. */
	private static void drawSlot(DrawContext context, int slotX, int slotY) {
		int x = slotX - 1;
		int y = slotY - 1;
		context.fill(x, y, x + 18, y + 18, SLOT_FILL);
		context.fill(x, y, x + 18, y + 1, SLOT_SHADOW);
		context.fill(x, y, x + 1, y + 18, SLOT_SHADOW);
		context.fill(x, y + 17, x + 18, y + 18, SLOT_HIGHLIGHT);
		context.fill(x + 17, y, x + 18, y + 18, SLOT_HIGHLIGHT);
	}
}
