package dev.wisteria.hud;

import dev.wisteria.Client;
import dev.wisteria.platform.Key;
import dev.wisteria.platform.ScreenBackend;
import dev.wisteria.render.Renderer2D;
import dev.wisteria.ui.Colors;
import dev.wisteria.ui.Metrics;
import dev.wisteria.ui.Metrics.Type;
import dev.wisteria.ui.Theme;
import dev.wisteria.ui.gui.Controls;
import dev.wisteria.ui.gui.Controls.Kind;
import dev.wisteria.ui.gui.Ui;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Where HUD elements are arranged: drag them, and they snap to the screen's edges and middle, to each other's
 * edges and centres, and to an 8-pixel grid (hold Shift to move freely). The anchor follows the third of the
 * screen an element is dropped in. Chips at the top switch elements on and off; right click puts one back.
 */
public final class HudEditorScreen implements ScreenBackend.ClientScreen {
	private static final float CHIP_HEIGHT = 15.0F;
	private static final float CHIP_GAP = 4.0F;

	private final Client client;
	private final Runnable onClosed;
	private final Controls controls = new Controls();
	private List<HudManager.Drawn> drawn = List.of();
	private final List<Chip> chips = new ArrayList<>();
	private @Nullable HudElement selected;
	private @Nullable HudElement dragging;
	private float grabX;
	private float grabY;
	private @Nullable HudElement resizing;
	private int resizeCorner;
	private HudLayout.@Nullable Rect resizeStart;
	private float resizeStartScale;
	private HudLayout.@Nullable Rect resetSizeBox;
	private @Nullable HudElement hovered;
	private float guideX = Float.NaN;
	private float guideY = Float.NaN;
	private float width;
	private float height;

	private record Chip(HudElement element, float x, float y, float width) {
	}

	public HudEditorScreen(Client client, Runnable onClosed) {
		this.client = client;
		this.onClosed = onClosed;
		client.hud().setEditing(true);
	}

	@Override
	public String title() {
		return "HUD editor";
	}

	@Override
	public boolean blurBackground() {
		return false;
	}

	public Controls controls() {
		return this.controls;
	}

	public @Nullable HudElement selected() {
		return this.selected;
	}

	@Override
	public void render(Renderer2D renderer, float mouseX, float mouseY, float tickDelta) {
		this.width = renderer.width();
		this.height = renderer.height();
		Theme theme = this.client.themes().current();
		HudManager hud = this.client.hud();
		this.controls.begin(mouseX, mouseY);

		renderer.rect(0.0F, 0.0F, this.width, this.height, Colors.withAlpha(theme.background(), 0x50));
		this.grid(renderer, theme);

		if (this.dragging != null) {
			this.drag(mouseX, mouseY);
		}

		if (this.resizing != null) {
			this.resize(mouseX, mouseY);
		}

		this.drawn = hud.render(renderer, true);
		HudManager.Drawn under = this.at(mouseX, mouseY);
		this.hovered = under != null ? under.element() : null;
		for (HudManager.Drawn item : this.drawn) {
			this.outline(renderer, theme, item);
		}

		this.handles(renderer, theme, mouseX, mouseY);

		if (!Float.isNaN(this.guideX)) {
			renderer.rect(this.guideX - 0.5F, 0.0F, 1.0F, this.height, Colors.withAlpha(theme.accent(), 0xC0));
		}

		if (!Float.isNaN(this.guideY)) {
			renderer.rect(0.0F, this.guideY - 0.5F, this.width, 1.0F, Colors.withAlpha(theme.accent(), 0xC0));
		}

		this.toolbar(renderer, theme);
		String hint = "Drag to move  ·  Corners or wheel: size  ·  Shift: no snapping  ·  Arrows: nudge  ·  Right click: reset  ·  Esc: done";
		Type.CAPTION.draw(renderer, hint, (this.width - Type.CAPTION.width(renderer, hint)) / 2.0F, this.height - 14.0F, theme.textMuted());
		this.controls.end();
	}

	private void grid(Renderer2D renderer, Theme theme) {
		int line = Colors.withAlpha(theme.outline(), 0x22);
		for (float x = HudLayout.GRID; x < this.width; x += HudLayout.GRID) {
			renderer.rect(x, 0.0F, 0.5F, this.height, line);
		}

		for (float y = HudLayout.GRID; y < this.height; y += HudLayout.GRID) {
			renderer.rect(0.0F, y, this.width, 0.5F, line);
		}

		int middle = Colors.withAlpha(theme.outline(), 0x60);
		renderer.rect(this.width / 2.0F - 0.5F, 0.0F, 1.0F, this.height, middle);
		renderer.rect(0.0F, this.height / 2.0F - 0.5F, this.width, 1.0F, middle);
	}

	private void outline(Renderer2D renderer, Theme theme, HudManager.Drawn item) {
		HudLayout.Rect rect = item.rect();
		HudElement element = item.element();
		Controls.State state = this.controls.register("hud:" + element.id(), Kind.BUTTON, rect.x(), rect.y(), rect.width(), rect.height(),
			() -> element == this.selected ? "selected" : "idle");
		boolean active = element == this.selected || element == this.dragging;
		int colour = active ? theme.accent() : state.hovered() ? Colors.withAlpha(theme.accent(), 0xA0) : Colors.withAlpha(theme.textMuted(), 0x70);
		renderer.roundedOutline(rect.x() - 2.0F, rect.y() - 2.0F, rect.width() + 4.0F, rect.height() + 4.0F, Metrics.Radius.M + 1.0F, active ? 1.5F : 1.0F, colour);
		if (active || state.hovered()) {
			HudLayout.Placement placement = this.client.hud().placement(element);
			String label = element.name() + "  " + placement.anchor().id();
			float labelWidth = Type.CAPTION.width(renderer, label) + 8.0F;
			float labelY = rect.y() > 16.0F ? rect.y() - 14.0F : rect.bottom() + 3.0F;
			float labelX = Math.max(0.0F, Math.min(rect.x(), this.width - labelWidth));
			renderer.roundedRect(labelX, labelY, labelWidth, 11.0F, 3.0F, theme.accent());
			Type.CAPTION.draw(renderer, label, labelX + 4.0F, labelY + 0.5F, theme.onAccent());
			// The anchor: a dot where the element is pinned on the screen.
			Anchor anchor = placement.anchor();
			renderer.roundedRect(anchor.fx() * this.width - 3.0F + (anchor.fx() == 0.0F ? 3.0F : anchor.fx() == 1.0F ? -3.0F : 0.0F),
				anchor.fy() * this.height - 3.0F + (anchor.fy() == 0.0F ? 3.0F : anchor.fy() == 1.0F ? -3.0F : 0.0F), 6.0F, 6.0F, 3.0F, theme.accent());
		}
	}

	private void toolbar(Renderer2D renderer, Theme theme) {
		this.chips.clear();
		List<HudElement> elements = this.client.hud().elements();
		List<Float> widths = new ArrayList<>();
		for (HudElement element : elements) {
			widths.add(Type.CAPTION.width(renderer, element.name()) + 18.0F);
		}

		// Rows of chips, centred, wrapping on narrow screens.
		float maxRow = Math.min(this.width - 16.0F, 640.0F);
		float y = 6.0F;
		int start = 0;
		while (start < elements.size()) {
			float rowWidth = 0.0F;
			int end = start;
			while (end < elements.size() && (end == start || rowWidth + CHIP_GAP + widths.get(end) <= maxRow)) {
				rowWidth += (end == start ? 0.0F : CHIP_GAP) + widths.get(end);
				end++;
			}

			float x = (this.width - rowWidth) / 2.0F;
			for (int i = start; i < end; i++) {
				this.chips.add(new Chip(elements.get(i), x, y, widths.get(i)));
				x += widths.get(i) + CHIP_GAP;
			}

			start = end;
			y += CHIP_HEIGHT + CHIP_GAP;
		}

		for (Chip chip : this.chips) {
			HudElement element = chip.element();
			Controls.State state = this.controls.register("hud-toggle:" + element.id(), Kind.TOGGLE, chip.x(), chip.y(), chip.width(), CHIP_HEIGHT,
				() -> element.isEnabled() ? "on" : "off");
			boolean on = element.isEnabled();
			int fill = on ? (state.hovered() ? theme.accentHover() : theme.accent()) : (state.hovered() ? theme.hover() : theme.raised());
			renderer.roundedRect(chip.x(), chip.y(), chip.width(), CHIP_HEIGHT, CHIP_HEIGHT / 2.0F, fill);
			renderer.roundedRect(chip.x() + 6.0F, chip.y() + CHIP_HEIGHT / 2.0F - 2.0F, 4.0F, 4.0F, 2.0F, on ? theme.onAccent() : theme.textMuted());
			Type.CAPTION.draw(renderer, element.name(), chip.x() + 13.0F, chip.y() + (CHIP_HEIGHT - Type.CAPTION.lineHeight(renderer)) / 2.0F,
				on ? theme.onAccent() : theme.text());
		}

		// Reset: only does something once an element has been moved.
		boolean anyMoved = this.client.hud().elements().stream().anyMatch(this.client.hud()::isMoved);
		String label = "Reset layout";
		float resetWidth = Type.CAPTION.width(renderer, label) + 14.0F;
		HudElement chosen = this.selected;
		boolean sized = chosen != null && Math.abs(chosen.scale() - 1.0F) > 0.001F;
		String sizeLabel = chosen != null ? "Reset size · " + HudScale.label(chosen.scale()) : "Reset size";
		float sizeWidth = Type.CAPTION.width(renderer, sizeLabel) + 14.0F;
		float resetX = (this.width - resetWidth - CHIP_GAP - sizeWidth) / 2.0F;
		Controls.State reset = this.controls.register("hud-reset", Kind.BUTTON, resetX, y, resetWidth, CHIP_HEIGHT, anyMoved,
			() -> this.client.hud().toJson().toString(), anyMoved ? null : "Nothing has been moved");
		int resetFill = !anyMoved ? Colors.withAlpha(theme.raised(), 0x80) : reset.hovered() ? theme.hover() : theme.raised();
		renderer.roundedRect(resetX, y, resetWidth, CHIP_HEIGHT, CHIP_HEIGHT / 2.0F, resetFill);
		renderer.roundedOutline(resetX, y, resetWidth, CHIP_HEIGHT, CHIP_HEIGHT / 2.0F, Metrics.Stroke.HAIRLINE, Colors.withAlpha(theme.outline(), 0xA0));
		Type.CAPTION.draw(renderer, label, resetX + 7.0F, y + (CHIP_HEIGHT - Type.CAPTION.lineHeight(renderer)) / 2.0F,
			anyMoved ? theme.text() : theme.textDisabled());
		this.resetBox = new HudLayout.Rect(resetX, y, resetWidth, CHIP_HEIGHT);

		// Reset size: for the selected element, once its size is not 1.
		float sizeX = resetX + resetWidth + CHIP_GAP;
		Controls.State resetSize = this.controls.register("hud-reset-size", Kind.BUTTON, sizeX, y, sizeWidth, CHIP_HEIGHT, sized,
			() -> chosen != null ? Float.toString(chosen.scale()) : "-", sized ? null : "Select an element whose size is not 1");
		int sizeFill = !sized ? Colors.withAlpha(theme.raised(), 0x80) : resetSize.hovered() ? theme.hover() : theme.raised();
		renderer.roundedRect(sizeX, y, sizeWidth, CHIP_HEIGHT, CHIP_HEIGHT / 2.0F, sizeFill);
		renderer.roundedOutline(sizeX, y, sizeWidth, CHIP_HEIGHT, CHIP_HEIGHT / 2.0F, Metrics.Stroke.HAIRLINE, Colors.withAlpha(theme.outline(), 0xA0));
		Type.CAPTION.draw(renderer, sizeLabel, sizeX + 7.0F, y + (CHIP_HEIGHT - Type.CAPTION.lineHeight(renderer)) / 2.0F,
			sized ? theme.text() : theme.textDisabled());
		this.resetSizeBox = new HudLayout.Rect(sizeX, y, sizeWidth, CHIP_HEIGHT);
	}

	private HudLayout.@Nullable Rect resetBox;

	private void drag(float mouseX, float mouseY) {
		HudElement element = this.dragging;
		HudManager.Drawn current = this.find(element);
		if (current == null) {
			return;
		}

		List<HudLayout.Rect> others = new ArrayList<>();
		for (HudManager.Drawn item : this.drawn) {
			if (item.element() != element) {
				others.add(item.rect());
			}
		}

		float x = mouseX - this.grabX;
		float y = mouseY - this.grabY;
		float w = current.rect().width();
		float h = current.rect().height();
		HudLayout.Snapped snapped = this.free()
			? new HudLayout.Snapped(Math.max(0.0F, Math.min(x, this.width - w)), Math.max(0.0F, Math.min(y, this.height - h)), Float.NaN, Float.NaN)
			: HudLayout.snap(x, y, w, h, others, this.width, this.height, true);
		this.guideX = snapped.guideX();
		this.guideY = snapped.guideY();
		this.client.hud().setPlacement(element, HudLayout.placementAt(snapped.x(), snapped.y(), w, h, this.width, this.height));
	}

	private HudManager.@Nullable Drawn find(@Nullable HudElement element) {
		for (HudManager.Drawn item : this.drawn) {
			if (item.element() == element) {
				return item;
			}
		}

		return null;
	}

	private HudManager.@Nullable Drawn at(float x, float y) {
		// Topmost first: the last drawn.
		for (int i = this.drawn.size() - 1; i >= 0; i--) {
			HudManager.Drawn item = this.drawn.get(i);
			if (item.rect().contains(x, y)) {
				return item;
			}
		}

		return null;
	}

	// Handles belong to the selected element, or to the one under the cursor when nothing is selected.
	private HudManager.@Nullable Drawn handleOwner() {
		HudManager.Drawn selected = this.find(this.selected);
		return selected != null ? selected : this.find(this.hovered);
	}

	private void handles(Renderer2D renderer, Theme theme, float mouseX, float mouseY) {
		HudManager.Drawn owner = this.resizing != null ? this.find(this.resizing) : this.handleOwner();
		if (owner == null) {
			return;
		}

		HudLayout.Rect rect = owner.rect();
		int hot = this.resizing != null ? this.resizeCorner : HudScale.handleAt(rect, mouseX, mouseY);
		for (int corner = 0; corner < 4; corner++) {
			float cx = HudScale.cornerX(rect, corner);
			float cy = HudScale.cornerY(rect, corner);
			float size = corner == hot ? HudScale.HANDLE + 2.0F : HudScale.HANDLE;
			renderer.roundedRect(cx - size / 2.0F, cy - size / 2.0F, size, size, 1.5F, theme.accent());
			renderer.roundedRect(cx - size / 2.0F + 1.5F, cy - size / 2.0F + 1.5F, size - 3.0F, size - 3.0F, 1.0F, corner == hot ? theme.accent() : theme.surface());
		}

		if (this.resizing != null) {
			String label = HudScale.label(this.resizing.scale());
			float width = Type.MONO.width(renderer, label) + 8.0F;
			float x = Math.min(mouseX + 10.0F, this.width - width);
			float y = Math.max(0.0F, mouseY - 18.0F);
			renderer.roundedRect(x, y, width, 13.0F, 3.0F, theme.accent());
			Type.MONO.draw(renderer, label, x + 4.0F, y + 1.0F, theme.onAccent());
		}
	}

	private boolean free() {
		return this.client.platform().input().isDown(Key.LEFT_SHIFT) || this.client.platform().input().isDown(Key.RIGHT_SHIFT);
	}

	private void resize(float mouseX, float mouseY) {
		HudElement element = this.resizing;
		HudLayout.Rect start = this.resizeStart;
		if (element == null || start == null) {
			return;
		}

		float ratio = HudScale.dragRatio(start, this.client.hud().placement(element).anchor(), this.resizeCorner, mouseX, mouseY);
		element.scaleSetting().set(HudScale.snap(this.resizeStartScale * ratio, this.free()));
	}

	@Override
	public boolean mouseScroll(float x, float y, float amount) {
		HudManager.Drawn item = this.at(x, y);
		if (item == null) {
			return false;
		}

		boolean control = this.client.platform().input().isDown(Key.LEFT_CONTROL) || this.client.platform().input().isDown(Key.RIGHT_CONTROL);
		HudElement element = item.element();
		element.scaleSetting().set(HudScale.wheel(element.scale(), amount, this.free(), control));
		this.selected = element;
		return true;
	}

	@Override
	public boolean mouseDown(float x, float y, ScreenBackend.MouseButton button) {
		this.controls.press(x, y);
		for (Chip chip : this.chips) {
			if (button == ScreenBackend.MouseButton.LEFT && Ui.inside(x, y, chip.x(), chip.y(), chip.width(), CHIP_HEIGHT)) {
				this.client.setEnabled(chip.element(), !chip.element().isEnabled());
				return true;
			}
		}

		if (button == ScreenBackend.MouseButton.LEFT && this.resetBox != null && this.resetBox.contains(x, y)) {
			this.client.hud().resetAll();
			return true;
		}

		if (button == ScreenBackend.MouseButton.LEFT && this.resetSizeBox != null && this.resetSizeBox.contains(x, y) && this.selected != null) {
			this.selected.scaleSetting().set(1.0F);
			return true;
		}

		HudManager.Drawn handled = this.handleOwner();
		if (button == ScreenBackend.MouseButton.LEFT && handled != null) {
			int corner = HudScale.handleAt(handled.rect(), x, y);
			if (corner >= 0) {
				this.selected = handled.element();
				this.resizing = handled.element();
				this.resizeCorner = corner;
				this.resizeStart = handled.rect();
				this.resizeStartScale = handled.element().scale();
				return true;
			}
		}

		HudManager.Drawn item = this.at(x, y);
		if (item == null) {
			this.selected = null;
			return false;
		}

		this.selected = item.element();
		if (button == ScreenBackend.MouseButton.RIGHT) {
			this.client.hud().reset(item.element());
		} else if (button == ScreenBackend.MouseButton.LEFT) {
			this.dragging = item.element();
			this.grabX = x - item.rect().x();
			this.grabY = y - item.rect().y();
		}

		return true;
	}

	@Override
	public void mouseUp(float x, float y, ScreenBackend.MouseButton button) {
		this.controls.release();
		this.dragging = null;
		this.resizing = null;
		this.guideX = Float.NaN;
		this.guideY = Float.NaN;
	}

	@Override
	public boolean keyDown(Key key, boolean shift, boolean control, boolean alt) {
		HudElement element = this.selected;
		if (element == null) {
			return false;
		}

		HudManager.Drawn item = this.find(element);
		if (item == null) {
			return false;
		}

		float step = shift ? HudLayout.GRID : 1.0F;
		float dx = key == Key.LEFT ? -step : key == Key.RIGHT ? step : 0.0F;
		float dy = key == Key.UP ? -step : key == Key.DOWN ? step : 0.0F;
		if (key == Key.DELETE || key == Key.BACKSPACE) {
			this.client.hud().reset(element);
			return true;
		}

		if (dx == 0.0F && dy == 0.0F) {
			return false;
		}

		// From where it is now, not where it was last drawn: several presses can come between frames.
		HudLayout.Rect rect = HudLayout.place(this.client.hud().placement(element), item.rect().width(), item.rect().height(), this.width, this.height);
		this.client.hud().setPlacement(element, HudLayout.placementAt(
			Math.max(0.0F, Math.min(rect.x() + dx, this.width - rect.width())), Math.max(0.0F, Math.min(rect.y() + dy, this.height - rect.height())),
			rect.width(), rect.height(), this.width, this.height));
		return true;
	}

	@Override
	public void onClose() {
		this.client.hud().setEditing(false);
		this.onClosed.run();
	}
}
