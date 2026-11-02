package com.example.tudursguns.client.screen;

import net.minecraft.client.gui.GuiGraphics;

/** Vanilla-looking item slot backgrounds for this mod's screens (drawn with fills, so no texture is
 * needed): an 18x18 recessed square - dark top/left edge, light bottom/right edge - around each slot.
 * Neighbouring slots 18 apart then read as a grid, as in vanilla containers. */
public final class SlotBackground {

	private SlotBackground() {
	}

	public static final int FILL = 0xFF8B8B8B;
	private static final int SHADOW = 0xFF373737;
	private static final int HIGHLIGHT = 0xFFFFFFFF;

	/** One slot around a Slot's own coordinate (slotX/slotY: the item's top-left corner, on screen). */
	public static void draw(GuiGraphics context, int slotX, int slotY) {
		int x = slotX - 1;
		int y = slotY - 1;
		context.fill(x, y, x + 18, y + 18, FILL);
		context.fill(x, y, x + 17, y + 1, SHADOW);
		context.fill(x, y, x + 1, y + 17, SHADOW);
		context.fill(x + 1, y + 17, x + 18, y + 18, HIGHLIGHT);
		context.fill(x + 17, y + 1, x + 18, y + 18, HIGHLIGHT);
	}
}
