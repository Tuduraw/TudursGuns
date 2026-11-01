package com.example.tudursguns.client.screen;

import com.example.tudursguns.block.SoldierPostBlockEntity;
import com.example.tudursguns.screen.SoldierPostScreenHandler;
import com.example.tudursguns.soldier.SoldierState;
import com.example.tudursvehiclemod.item.ModItems;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Soldier post: armor down the left, the weapon and a Drone Route Book beside it, food on the right; what the soldier is
 * doing in the middle; buttons to send it out / stand it down and to edit the route. Drawn with plain
 * rectangles like the other screens - no texture needed. */
public class SoldierPostScreen extends AbstractContainerScreen<SoldierPostScreenHandler> {

	private static final int PANEL_COLOR = 0xFFC6C6C6;
	private static final int SLOT_COLOR = 0xFF8B8B8B;
	private static final int TEXT_COLOR = 0xFF404040;
	private static final int STATUS_X = 52;
	private static final int LINE_HEIGHT = 11;
	private static final int BUTTON_WIDTH = 54;

	private Button toggleButton;

	public SoldierPostScreen(SoldierPostScreenHandler handler, Inventory inventory, Component title) {
		super(handler, inventory, title);
		this.imageWidth = 200;
		this.imageHeight = 182;
		this.inventoryLabelY = SoldierPostScreenHandler.PLAYER_INVENTORY_Y - 11;
	}

	@Override
	protected void init() {
		super.init();
		int buttonX = this.leftPos + SoldierPostScreenHandler.FOOD_X - 1;
		this.toggleButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> click(SoldierPostScreenHandler.BUTTON_TOGGLE))
				.bounds(buttonX, this.topPos + 58, BUTTON_WIDTH, 16).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.tudursguns.soldier_post.edit_route"),
						button -> click(SoldierPostScreenHandler.BUTTON_ROUTE))
				.bounds(buttonX, this.topPos + 76, BUTTON_WIDTH, 16).build());
	}

	private void click(int id) {
		if (this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
		}
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		boolean active = this.menu.property(SoldierPostBlockEntity.PROPERTY_ACTIVE) != 0;
		this.toggleButton.setMessage(Component.translatable(active ? "gui.tudursguns.soldier_post.stand_down" : "gui.tudursguns.soldier_post.deploy"));
		super.render(context, mouseX, mouseY, delta);
		this.renderTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
		context.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, PANEL_COLOR);
		for (Slot slot : this.menu.slots) {
			context.fill(this.leftPos + slot.x - 1, this.topPos + slot.y - 1, this.leftPos + slot.x + 17, this.topPos + slot.y + 17, SLOT_COLOR);
		}
		// A faded route book marks the book slot while it's empty.
		Slot book = this.menu.slots.get(SoldierPostBlockEntity.BOOK_SLOT);
		if (!book.hasItem()) {
			int bookX = this.leftPos + book.x;
			int bookY = this.topPos + book.y;
			context.renderItem(new ItemStack(ModItems.DRONE_ROUTE_BOOK), bookX, bookY);
			context.fill(bookX, bookY, bookX + 16, bookY + 16, SLOT_COLOR & 0xB0FFFFFF);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics context, int mouseX, int mouseY) {
		context.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT_COLOR, false);
		context.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, TEXT_COLOR, false);

		SoldierState[] states = SoldierState.values();
		SoldierState state = states[Math.floorMod(this.menu.property(SoldierPostBlockEntity.PROPERTY_STATE), states.length)];
		int y = SoldierPostScreenHandler.TOP_Y;
		line(context, Component.translatable("gui.tudursguns.soldier_post.state." + state.name().toLowerCase(Locale.ROOT)), y);
		y += LINE_HEIGHT;
		int health = this.menu.property(SoldierPostBlockEntity.PROPERTY_HEALTH_TENTHS);
		if (health > 0) {
			line(context, Component.translatable("gui.tudursguns.soldier_post.health", tenths(health)), y);
			y += LINE_HEIGHT;
		}
		if (state == SoldierState.DOWN) {
			line(context, Component.translatable("gui.tudursguns.soldier_post.respawn", this.menu.property(SoldierPostBlockEntity.PROPERTY_RESPAWN_SECONDS)), y);
			y += LINE_HEIGHT;
		}
		line(context, Component.translatable("gui.tudursguns.soldier_post.food", tenths(this.menu.property(SoldierPostBlockEntity.PROPERTY_FOOD_TENTHS)),
				(int) SoldierPostBlockEntity.MAX_FOOD), y);
		y += LINE_HEIGHT;
		boolean fromBook = this.menu.property(SoldierPostBlockEntity.PROPERTY_ROUTE_FROM_BOOK) != 0;
		line(context, Component.translatable(fromBook ? "gui.tudursguns.soldier_post.route_book" : "gui.tudursguns.soldier_post.route",
				this.menu.property(SoldierPostBlockEntity.PROPERTY_ROUTE_SIZE)), y);
		y += LINE_HEIGHT;
		line(context, Component.translatable("gui.tudursguns.soldier_post.engage_range", this.menu.property(SoldierPostBlockEntity.PROPERTY_ENGAGE_RANGE)), y);
	}

	private void line(GuiGraphics context, Component text, int y) {
		int width = SoldierPostScreenHandler.FOOD_X - STATUS_X - 4;
		context.drawString(this.font, this.font.substrByWidth(text, width).getString(), STATUS_X, y, TEXT_COLOR, false);
	}

	private static String tenths(int value) {
		return String.format(Locale.ROOT, "%.1f", value / 10f);
	}
}
