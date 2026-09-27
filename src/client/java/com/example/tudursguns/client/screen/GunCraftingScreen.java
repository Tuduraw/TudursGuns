package com.example.tudursguns.client.screen;

import com.example.tudursguns.handheld.GunRecipeDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.screen.GunCraftingScreenHandler;
import com.example.tudursguns.weapon.GunCrafting;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/** Recipe list on the left (result icon and name, grouped under category headings), the selected
 * recipe on the right: its ingredients with how many the player has, and a craft button. Drawn with
 * plain rectangles like the weapon workbench - no texture needed. */
public class GunCraftingScreen extends HandledScreen<GunCraftingScreenHandler> {

	private static final int PANEL_COLOR = 0xFFC6C6C6;
	private static final int LIST_COLOR = 0xFF8B8B8B;
	private static final int ROW_HOVER = 0xFFA0A0A0;
	private static final int ROW_SELECTED = 0xFFE0E0E0;
	private static final int TEXT_COLOR = 0xFF404040;
	private static final int ROW_TEXT = 0xFFFFFFFF;
	private static final int HEADING_TEXT = 0xFFFFFF80;
	private static final int ENOUGH_COLOR = 0xFF207020;
	private static final int MISSING_COLOR = 0xFFA02020;

	private static final int LIST_X = 6;
	private static final int LIST_Y = 18;
	private static final int LIST_WIDTH = 116;
	private static final int ROW_HEIGHT = 18;
	private static final int VISIBLE_ROWS = 8;
	private static final int DETAIL_X = 128;
	private static final int INGREDIENT_ROW = 17;
	private static final int VISIBLE_INGREDIENTS = 6;

	private int scroll;
	/** The selected recipe (by id, so a re-sync while the screen is open can't swap it for another). */
	private Identifier selected;
	private ButtonWidget craftButton;

	public GunCraftingScreen(GunCraftingScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 256;
		this.backgroundHeight = 176;
	}

	@Override
	protected void init() {
		super.init();
		this.craftButton = this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursguns.gun_crafting.craft"), button -> craft())
				.dimensions(this.x + DETAIL_X, this.y + this.backgroundHeight - 26, this.backgroundWidth - DETAIL_X - 6, 20)
				.build());
	}

	/** One line of the list: a category heading or a recipe (by its position in GunCrafting.recipeIds). */
	private record Row(String heading, int recipe) {
	}

	private List<Row> rows(List<Identifier> ids) {
		List<Row> rows = new ArrayList<>();
		String category = null;
		for (int i = 0; i < ids.size(); i++) {
			GunRecipeDefinition recipe = ModDefinitions.GUN_RECIPES.client().get(ids.get(i));
			String next = recipe.category().orElse("");
			if (!next.equals(category)) {
				category = next;
				if (!next.isEmpty()) {
					rows.add(new Row(next, -1));
				}
			}
			rows.add(new Row(null, i));
		}
		return rows;
	}

	private GunRecipeDefinition selectedRecipe() {
		return this.selected == null ? null : ModDefinitions.GUN_RECIPES.client().get(this.selected);
	}

	/** The server is told the recipe's position in the list (see GunCraftingScreenHandler.onButtonClick). */
	private void craft() {
		int index = GunCrafting.recipeIds(true).indexOf(this.selected);
		if (index >= 0 && this.client != null && this.client.interactionManager != null && this.client.player != null
				&& this.handler.onButtonClick(this.client.player, index)) {
			this.client.interactionManager.clickButton(this.handler.syncId, index);
		}
	}


	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		GunRecipeDefinition recipe = selectedRecipe();
		if (this.craftButton != null) {
			this.craftButton.active = recipe != null && this.client != null && this.client.player != null
					&& GunCrafting.canCraft(this.client.player, recipe);
		}
		super.render(context, mouseX, mouseY, delta);
		ItemStack hovered = hoveredStack(mouseX, mouseY);
		if (!hovered.isEmpty()) {
			context.drawItemTooltip(this.textRenderer, hovered, mouseX, mouseY);
		}
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int x = this.x;
		int y = this.y;
		context.fill(x, y, x + this.backgroundWidth, y + this.backgroundHeight, PANEL_COLOR);
		int listTop = y + LIST_Y;
		int listBottom = listTop + VISIBLE_ROWS * ROW_HEIGHT;
		context.fill(x + LIST_X - 1, listTop - 1, x + LIST_X + LIST_WIDTH + 1, listBottom + 1, LIST_COLOR);

		List<Identifier> ids = GunCrafting.recipeIds(true);
		if (ids.isEmpty()) {
			context.drawText(this.textRenderer, Text.translatable("gui.tudursguns.gun_crafting.no_recipes"), x + LIST_X + 2, listTop + 4, ROW_TEXT, false);
			return;
		}
		List<Row> rows = rows(ids);
		this.scroll = Math.max(0, Math.min(this.scroll, rows.size() - VISIBLE_ROWS));
		for (int i = 0; i < VISIBLE_ROWS && this.scroll + i < rows.size(); i++) {
			Row row = rows.get(this.scroll + i);
			int rowY = listTop + i * ROW_HEIGHT;
			if (row.heading() != null) {
				Text heading = Text.translatableWithFallback("gui.tudursguns.gun_crafting.category." + row.heading(), row.heading());
				context.drawText(this.textRenderer, heading, x + LIST_X + 2, rowY + 5, HEADING_TEXT, false);
				continue;
			}
			boolean hover = mouseX >= x + LIST_X && mouseX < x + LIST_X + LIST_WIDTH && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
			if (ids.get(row.recipe()).equals(this.selected)) {
				context.fill(x + LIST_X, rowY, x + LIST_X + LIST_WIDTH, rowY + ROW_HEIGHT, ROW_SELECTED);
			} else if (hover) {
				context.fill(x + LIST_X, rowY, x + LIST_X + LIST_WIDTH, rowY + ROW_HEIGHT, ROW_HOVER);
			}
			ItemStack result = ModDefinitions.GUN_RECIPES.client().get(ids.get(row.recipe())).result().createStack();
			context.drawItem(result, x + LIST_X + 1, rowY + 1);
			String name = this.textRenderer.trimToWidth(result.getName().getString(), LIST_WIDTH - 22);
			context.drawText(this.textRenderer, name, x + LIST_X + 20, rowY + 5, ids.get(row.recipe()).equals(this.selected) ? TEXT_COLOR : ROW_TEXT, false);
		}

		GunRecipeDefinition recipe = selectedRecipe();
		if (recipe == null || this.client == null || this.client.player == null) {
			context.drawText(this.textRenderer, Text.translatable("gui.tudursguns.gun_crafting.select"), x + DETAIL_X, listTop + 4, TEXT_COLOR, false);
			return;
		}
		ItemStack result = recipe.result().createStack();
		context.drawItem(result, x + DETAIL_X, listTop);
		context.drawStackOverlay(this.textRenderer, result, x + DETAIL_X, listTop);
		String name = this.textRenderer.trimToWidth(result.getName().getString(), this.backgroundWidth - DETAIL_X - 26);
		context.drawText(this.textRenderer, name, x + DETAIL_X + 20, listTop + 4, TEXT_COLOR, false);
		List<GunRecipeDefinition.ItemRef> ingredients = recipe.ingredients();
		for (int i = 0; i < ingredients.size() && i < VISIBLE_INGREDIENTS; i++) {
			GunRecipeDefinition.ItemRef ingredient = ingredients.get(i);
			int rowY = listTop + 22 + i * INGREDIENT_ROW;
			ItemStack shown = displayed(ingredient);
			if (!shown.isEmpty()) {
				context.drawItem(shown, x + DETAIL_X, rowY);
			}
			int have = GunCrafting.count(this.client.player, ingredient);
			boolean enough = this.client.player.isCreative() || have >= ingredient.count();
			String label = Math.min(have, 9999) + " / " + ingredient.count();
			context.drawText(this.textRenderer, label, x + DETAIL_X + 20, rowY + 4, enough ? ENOUGH_COLOR : MISSING_COLOR, false);
		}
		if (ingredients.size() > VISIBLE_INGREDIENTS) {
			context.drawText(this.textRenderer, "+" + (ingredients.size() - VISIBLE_INGREDIENTS),
					x + DETAIL_X + 80, listTop + 22 + (VISIBLE_INGREDIENTS - 1) * INGREDIENT_ROW + 4, TEXT_COLOR, false);
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, TEXT_COLOR, false);
	}

	/** The stack an ingredient is shown as - a tag cycles through its items once a second. */
	private static ItemStack displayed(GunRecipeDefinition.ItemRef ingredient) {
		List<ItemStack> stacks = ingredient.displayStacks();
		if (stacks.isEmpty()) {
			return ItemStack.EMPTY;
		}
		return stacks.get((int) ((Util.getMeasuringTimeMs() / 1000L) % stacks.size()));
	}

	/** The result or ingredient under the mouse, for its tooltip. */
	private ItemStack hoveredStack(int mouseX, int mouseY) {
		int listTop = this.y + LIST_Y;
		int left = this.x + DETAIL_X;
		if (mouseX < left || mouseX >= left + 16) {
			return ItemStack.EMPTY;
		}
		GunRecipeDefinition recipe = selectedRecipe();
		if (recipe == null) {
			return ItemStack.EMPTY;
		}
		if (mouseY >= listTop && mouseY < listTop + 16) {
			return recipe.result().createStack();
		}
		for (int i = 0; i < recipe.ingredients().size() && i < VISIBLE_INGREDIENTS; i++) {
			int rowY = listTop + 22 + i * INGREDIENT_ROW;
			if (mouseY >= rowY && mouseY < rowY + 16) {
				return displayed(recipe.ingredients().get(i));
			}
		}
		return ItemStack.EMPTY;
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		int listTop = this.y + LIST_Y;
		int mouseX = (int) click.x();
		int mouseY = (int) click.y();
		if (click.button() == 0 && mouseX >= this.x + LIST_X && mouseX < this.x + LIST_X + LIST_WIDTH
				&& mouseY >= listTop && mouseY < listTop + VISIBLE_ROWS * ROW_HEIGHT) {
			List<Row> rows = rows(GunCrafting.recipeIds(true));
			int index = this.scroll + (mouseY - listTop) / ROW_HEIGHT;
			if (index < rows.size() && rows.get(index).heading() == null) {
				this.selected = GunCrafting.recipeIds(true).get(rows.get(index).recipe());
			}
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseX >= this.x + LIST_X && mouseX < this.x + LIST_X + LIST_WIDTH) {
			this.scroll = Math.max(0, this.scroll - (int) Math.signum(verticalAmount));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}
}
