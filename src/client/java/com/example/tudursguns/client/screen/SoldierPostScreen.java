package com.example.tudursguns.client.screen;

import com.example.tudursguns.block.SoldierPostBlockEntity;
import com.example.tudursguns.screen.SoldierPostScreenHandler;
import com.example.tudursguns.soldier.SoldierState;
import com.example.tudursvehiclemod.item.ModItems;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.Locale;

/** Soldier post: armor down the left, the weapon and a Drone Route Book beside it, food on the right; what the soldier is
 * doing in the middle; buttons to send it out / stand it down and to edit the route. Drawn with plain
 * rectangles like the other screens - no texture needed. */
public class SoldierPostScreen extends HandledScreen<SoldierPostScreenHandler> {

	private static final int PANEL_COLOR = 0xFFC6C6C6;
	private static final int SLOT_COLOR = 0xFF8B8B8B;
	private static final int TEXT_COLOR = 0xFF404040;
	private static final int STATUS_X = 52;
	private static final int LINE_HEIGHT = 11;
	private static final int BUTTON_WIDTH = 54;

	private ButtonWidget toggleButton;

	public SoldierPostScreen(SoldierPostScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 200;
		this.backgroundHeight = 182;
		this.playerInventoryTitleY = SoldierPostScreenHandler.PLAYER_INVENTORY_Y - 11;
	}

	@Override
	protected void init() {
		super.init();
		int buttonX = this.x + SoldierPostScreenHandler.FOOD_X - 1;
		this.toggleButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), button -> click(SoldierPostScreenHandler.BUTTON_TOGGLE))
				.dimensions(buttonX, this.y + 58, BUTTON_WIDTH, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursguns.soldier_post.edit_route"),
						button -> click(SoldierPostScreenHandler.BUTTON_ROUTE))
				.dimensions(buttonX, this.y + 76, BUTTON_WIDTH, 16).build());
	}

	private void click(int id) {
		if (this.client != null && this.client.interactionManager != null) {
			this.client.interactionManager.clickButton(this.handler.syncId, id);
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		boolean active = this.handler.property(SoldierPostBlockEntity.PROPERTY_ACTIVE) != 0;
		this.toggleButton.setMessage(Text.translatable(active ? "gui.tudursguns.soldier_post.stand_down" : "gui.tudursguns.soldier_post.deploy"));
		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		context.fill(this.x, this.y, this.x + this.backgroundWidth, this.y + this.backgroundHeight, PANEL_COLOR);
		for (Slot slot : this.handler.slots) {
			context.fill(this.x + slot.x - 1, this.y + slot.y - 1, this.x + slot.x + 17, this.y + slot.y + 17, SLOT_COLOR);
		}
		// A faded route book marks the book slot while it's empty.
		Slot book = this.handler.slots.get(SoldierPostBlockEntity.BOOK_SLOT);
		if (!book.hasStack()) {
			int bookX = this.x + book.x;
			int bookY = this.y + book.y;
			context.drawItem(new ItemStack(ModItems.DRONE_ROUTE_BOOK), bookX, bookY);
			context.fill(bookX, bookY, bookX + 16, bookY + 16, SLOT_COLOR & 0xB0FFFFFF);
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, TEXT_COLOR, false);
		context.drawText(this.textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX, this.playerInventoryTitleY, TEXT_COLOR, false);

		SoldierState[] states = SoldierState.values();
		SoldierState state = states[Math.floorMod(this.handler.property(SoldierPostBlockEntity.PROPERTY_STATE), states.length)];
		int y = SoldierPostScreenHandler.TOP_Y;
		line(context, Text.translatable("gui.tudursguns.soldier_post.state." + state.name().toLowerCase(Locale.ROOT)), y);
		y += LINE_HEIGHT;
		int health = this.handler.property(SoldierPostBlockEntity.PROPERTY_HEALTH_TENTHS);
		if (health > 0) {
			line(context, Text.translatable("gui.tudursguns.soldier_post.health", tenths(health)), y);
			y += LINE_HEIGHT;
		}
		if (state == SoldierState.DOWN) {
			line(context, Text.translatable("gui.tudursguns.soldier_post.respawn", this.handler.property(SoldierPostBlockEntity.PROPERTY_RESPAWN_SECONDS)), y);
			y += LINE_HEIGHT;
		}
		line(context, Text.translatable("gui.tudursguns.soldier_post.food", tenths(this.handler.property(SoldierPostBlockEntity.PROPERTY_FOOD_TENTHS)),
				(int) SoldierPostBlockEntity.MAX_FOOD), y);
		y += LINE_HEIGHT;
		boolean fromBook = this.handler.property(SoldierPostBlockEntity.PROPERTY_ROUTE_FROM_BOOK) != 0;
		line(context, Text.translatable(fromBook ? "gui.tudursguns.soldier_post.route_book" : "gui.tudursguns.soldier_post.route",
				this.handler.property(SoldierPostBlockEntity.PROPERTY_ROUTE_SIZE)), y);
		y += LINE_HEIGHT;
		line(context, Text.translatable("gui.tudursguns.soldier_post.engage_range", this.handler.property(SoldierPostBlockEntity.PROPERTY_ENGAGE_RANGE)), y);
	}

	private void line(DrawContext context, Text text, int y) {
		int width = SoldierPostScreenHandler.FOOD_X - STATUS_X - 4;
		context.drawText(this.textRenderer, this.textRenderer.trimToWidth(text, width).getString(), STATUS_X, y, TEXT_COLOR, false);
	}

	private static String tenths(int value) {
		return String.format(Locale.ROOT, "%.1f", value / 10f);
	}
}
