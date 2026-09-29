package com.example.tudursguns.client.screen;

import com.example.tudursguns.block.SoldierPostBlockEntity;
import com.example.tudursguns.network.SoldierRoutePayload;
import com.example.tudursguns.soldier.SoldierWaypoint;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** A soldier post's route editor, one point at a time: its position relative to the post (X/Y/Z) and
 * how long the soldier waits there (seconds). "Here" fills in where the player stands. The route is
 * walked in a loop. Save sends it to the server, which checks it and reopens the post's screen;
 * Cancel just reopens it. */
public class SoldierRouteScreen extends Screen {

	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int LABEL_COLOR = 0xFFA0A0A0;
	private static final int FIELD_WIDTH = 44;

	private final BlockPos post;
	private final SoldierRoutePayload original;
	/** Points as {x, y, z, wait seconds}. */
	private final List<int[]> points = new ArrayList<>();
	private int index;
	private TextFieldWidget[] pointFields;
	private TextFieldWidget rangeField;
	/** True while the fields are being filled in from the current point (not edited by the player). */
	private boolean filling;

	public SoldierRouteScreen(SoldierRoutePayload payload) {
		super(Text.translatable("gui.tudursguns.soldier_route.title"));
		this.post = payload.pos();
		this.original = payload;
		for (SoldierWaypoint point : payload.route()) {
			this.points.add(new int[]{point.x(), point.y(), point.z(), point.waitTicks() / 20});
		}
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int top = this.height / 2 - 80;

		this.addDrawableChild(ButtonWidget.builder(Text.literal("<"), button -> page(-1)).dimensions(centerX - 100, top + 20, 20, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal(">"), button -> page(1)).dimensions(centerX + 80, top + 20, 20, 20).build());

		this.pointFields = new TextFieldWidget[4];
		for (int i = 0; i < 4; i++) {
			final int component = i;
			TextFieldWidget field = new TextFieldWidget(this.textRenderer, centerX - 100 + i * 52, top + 58, FIELD_WIDTH, 18, Text.empty());
			field.setMaxLength(5);
			field.setTextPredicate(text -> text.matches(component == 3 ? "\\d*" : "-?\\d*"));
			field.setChangedListener(text -> {
				if (!this.filling && this.index < this.points.size()) {
					this.points.get(this.index)[component] = parse(text, 0);
				}
			});
			this.pointFields[i] = this.addDrawableChild(field);
		}
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursguns.soldier_route.here"), button -> setHere())
				.dimensions(centerX - 100, top + 82, 64, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursguns.soldier_route.add"), button -> add())
				.dimensions(centerX - 32, top + 82, 64, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursguns.soldier_route.remove"), button -> remove())
				.dimensions(centerX + 36, top + 82, 64, 20).build());

		this.rangeField = new TextFieldWidget(this.textRenderer, centerX + 56, top + 114, FIELD_WIDTH, 18, Text.empty());
		this.rangeField.setMaxLength(3);
		this.rangeField.setTextPredicate(text -> text.matches("\\d*"));
		this.rangeField.setText(Integer.toString(this.original.engageRange()));
		this.addDrawableChild(this.rangeField);

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.tudursguns.soldier_route.save"), button -> send(true))
				.dimensions(centerX - 100, top + 144, 98, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> send(false))
				.dimensions(centerX + 2, top + 144, 98, 20).build());
		fill();
	}

	private void page(int step) {
		if (!this.points.isEmpty()) {
			this.index = Math.floorMod(this.index + step, this.points.size());
			fill();
		}
	}

	/** Where the player stands, relative to the post. */
	private int[] here() {
		if (this.client == null || this.client.player == null) {
			return new int[]{0, 1, 0, 0};
		}
		BlockPos at = this.client.player.getBlockPos().subtract(this.post);
		return new int[]{at.getX(), at.getY(), at.getZ(), 0};
	}

	private void setHere() {
		if (this.points.isEmpty()) {
			add();
			return;
		}
		int[] at = here();
		int[] point = this.points.get(this.index);
		System.arraycopy(at, 0, point, 0, 3);
		fill();
	}

	/** A new point (where the player stands) after the current one. */
	private void add() {
		if (this.points.size() >= SoldierPostBlockEntity.MAX_WAYPOINTS) {
			return;
		}
		this.index = this.points.isEmpty() ? 0 : this.index + 1;
		this.points.add(this.index, here());
		fill();
	}

	private void remove() {
		if (!this.points.isEmpty()) {
			this.points.remove(this.index);
			this.index = Math.max(0, Math.min(this.index, this.points.size() - 1));
			fill();
		}
	}

	/** Shows the current point in the fields (empty and locked with no points). */
	private void fill() {
		this.filling = true;
		boolean any = !this.points.isEmpty();
		for (int i = 0; i < 4; i++) {
			this.pointFields[i].setText(any ? Integer.toString(this.points.get(this.index)[i]) : "");
			this.pointFields[i].setEditable(any);
		}
		this.filling = false;
	}

	private void send(boolean save) {
		SoldierRoutePayload payload = this.original;
		if (save) {
			List<SoldierWaypoint> route = new ArrayList<>();
			for (int[] point : this.points) {
				route.add(new SoldierWaypoint(point[0], point[1], point[2], point[3] * 20).clamped());
			}
			payload = new SoldierRoutePayload(this.post, route, parse(this.rangeField.getText(), this.original.engageRange()));
		}
		ClientPlayNetworking.send(payload);
		this.close();
	}

	private static int parse(String text, int fallback) {
		try {
			return Integer.parseInt(text);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		int centerX = this.width / 2;
		int top = this.height / 2 - 80;
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, top, TEXT_COLOR);
		Text page = this.points.isEmpty()
				? Text.translatable("gui.tudursguns.soldier_route.empty")
				: Text.translatable("gui.tudursguns.soldier_route.point", this.index + 1, this.points.size());
		context.drawCenteredTextWithShadow(this.textRenderer, page, centerX, top + 26, TEXT_COLOR);
		String[] labels = {"X", "Y", "Z", Text.translatable("gui.tudursguns.soldier_route.wait").getString()};
		for (int i = 0; i < 4; i++) {
			context.drawTextWithShadow(this.textRenderer, labels[i], centerX - 100 + i * 52, top + 47, LABEL_COLOR);
		}
		context.drawTextWithShadow(this.textRenderer, Text.translatable("gui.tudursguns.soldier_route.relative"), centerX - 100, top + 104, LABEL_COLOR);
		context.drawTextWithShadow(this.textRenderer, Text.translatable("gui.tudursguns.soldier_route.engage_range",
				SoldierPostBlockEntity.MIN_ENGAGE_RANGE, SoldierPostBlockEntity.MAX_ENGAGE_RANGE), centerX - 100, top + 119, TEXT_COLOR);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
