package dev.wisteria.ui.gui;

import dev.wisteria.Client;
import dev.wisteria.platform.InputBackend;
import dev.wisteria.platform.Key;
import dev.wisteria.platform.ScreenBackend.MouseButton;
import dev.wisteria.module.Category;
import dev.wisteria.module.Module;
import dev.wisteria.ui.Animation;
import dev.wisteria.ui.Colors;
import dev.wisteria.ui.Easing;
import dev.wisteria.ui.Metrics.Radius;
import dev.wisteria.ui.Metrics.Stroke;
import dev.wisteria.ui.Metrics.Type;
import dev.wisteria.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** A draggable window listing one category's modules. Right-click the header to fold it away. */
final class Panel {
	static final float WIDTH = 118.0F;
	static final float HEADER = 22.0F;
	private static final float BOTTOM_PADDING = 4.0F;

	private final Category category;
	private final List<ModuleRow> rows = new ArrayList<>();
	private final Animation fold;
	private final Animation scroll = new Animation(0.0F, Animation.FAST, Easing.OUT_CUBIC);
	private float x;
	private float y;
	private boolean collapsed;
	private boolean dragging;
	private float grabX;
	private float grabY;
	private float bodyHeight;
	private float contentHeight;
	private float scrollTarget;

	Panel(Client client, Category category, List<Module> modules, float x, float y, boolean collapsed, Consumer<String> hint) {
		this.category = category;
		this.x = x;
		this.y = y;
		this.collapsed = collapsed;
		this.fold = new Animation(collapsed ? 0.0F : 1.0F, Animation.NORMAL, Easing.OUT_CUBIC);
		for (Module module : modules) {
			this.rows.add(new ModuleRow(client, module, hint));
		}
	}

	Category category() {
		return this.category;
	}

	float x() {
		return this.x;
	}

	float y() {
		return this.y;
	}

	boolean isCollapsed() {
		return this.collapsed;
	}

	boolean isDragging() {
		return this.dragging;
	}

	List<ModuleRow> rows() {
		return this.rows;
	}

	boolean contains(float px, float py) {
		return Ui.inside(px, py, this.x, this.y, WIDTH, HEADER + this.bodyHeight);
	}

	/**
	 * @param filter which modules to list (the search box)
	 * @param interactive false while another panel is on top of this one under the cursor
	 */
	void render(Ui ui, Predicate<Module> filter, boolean interactive) {
		Theme theme = ui.theme();
		float screenWidth = ui.renderer().width();
		float screenHeight = ui.renderer().height();
		if (this.dragging) {
			this.x = ui.mouseX() - this.grabX;
			this.y = ui.mouseY() - this.grabY;
		}

		// A panel can be pushed to an edge but never lost off-screen.
		this.x = Math.max(2.0F - WIDTH + 24.0F, Math.min(screenWidth - 24.0F, this.x));
		this.y = Math.max(2.0F, Math.min(screenHeight - HEADER - 2.0F, this.y));

		List<ModuleRow> visible = this.rows.stream().filter(row -> filter.test(row.module())).toList();
		this.contentHeight = BOTTOM_PADDING;
		for (ModuleRow row : visible) {
			this.contentHeight += row.height();
		}

		this.fold.animateTo(!this.collapsed);
		float open = this.fold.value();
		float room = Math.max(HEADER, screenHeight - this.y - HEADER - 20.0F);
		float viewHeight = Math.min(this.contentHeight, room);
		this.bodyHeight = viewHeight * open;
		this.scrollTarget = Math.max(0.0F, Math.min(this.contentHeight - viewHeight, this.scrollTarget));
		this.scroll.animateTo(this.scrollTarget);
		float offset = this.scroll.value();

		float height = HEADER + this.bodyHeight;
		ui.shadow(this.x, this.y + 3.0F, WIDTH, height, Radius.L, 14.0F, Colors.withAlpha(theme.shadow(), 130));
		ui.rounded(this.x, this.y, WIDTH, height, Radius.L, Colors.withAlpha(theme.surface(), 244));
		ui.outline(this.x, this.y, WIDTH, height, Radius.L, Stroke.HAIRLINE, theme.outline());

		ui.textInRow(Type.LABEL, this.category.title(), this.x + 10.0F, this.y, HEADER, theme.text());
		long active = visible.stream().filter(row -> row.module().isEnabled()).count();
		String count = active + "/" + visible.size();
		ui.textRightInRow(Type.MONO, count, this.x + WIDTH - 20.0F, this.y, HEADER, active > 0 ? theme.accent() : theme.textDisabled());
		ui.chevron(this.x + WIDTH - 10.0F, this.y + HEADER / 2.0F, 4.0F, open * (float)(Math.PI / 2.0), theme.textMuted());
		if (this.bodyHeight <= 0.5F) {
			return;
		}

		ui.rect(this.x + 8.0F, this.y + HEADER - 0.5F, WIDTH - 16.0F, Stroke.HAIRLINE, Colors.fade(theme.outline(), open));
		ui.renderer().pushScissor(this.x, this.y + HEADER, WIDTH, this.bodyHeight);
		Ui body = new Ui(ui.renderer(), theme, ui.mouseX(), ui.mouseY(), ui.opacity() * open);
		boolean inBody = interactive && !this.collapsed && ui.hovering(this.x, this.y + HEADER, WIDTH, this.bodyHeight);
		float rowY = this.y + HEADER + 1.0F - offset;
		for (ModuleRow row : visible) {
			row.render(body, this.x, rowY, WIDTH, inBody);
			rowY += row.height();
		}

		ui.renderer().popScissor();

		// A thin scrollbar, only when there is something to scroll.
		if (this.contentHeight > viewHeight + 0.5F && open > 0.9F) {
			float track = this.bodyHeight - 6.0F;
			float thumb = Math.max(10.0F, track * viewHeight / this.contentHeight);
			float travel = (track - thumb) * offset / (this.contentHeight - viewHeight);
			ui.rounded(this.x + WIDTH - 3.5F, this.y + HEADER + 3.0F + travel, 2.0F, thumb, 1.0F, Colors.withAlpha(theme.textMuted(), 120));
		}
	}

	boolean mouseDown(float px, float py, MouseButton button, Predicate<Module> filter) {
		if (!this.contains(px, py)) {
			return false;
		}

		if (py < this.y + HEADER) {
			if (button == MouseButton.LEFT) {
				this.dragging = true;
				this.grabX = px - this.x;
				this.grabY = py - this.y;
			} else if (button == MouseButton.RIGHT) {
				this.collapsed = !this.collapsed;
				if (this.collapsed) {
					this.blur();
				}
			}

			return true;
		}

		if (!this.collapsed) {
			for (ModuleRow row : this.rows) {
				if (filter.test(row.module()) && row.mouseDown(px, py, button)) {
					return true;
				}
			}
		}

		return true;
	}

	void mouseUp() {
		this.dragging = false;
		this.rows.forEach(ModuleRow::mouseUp);
	}

	boolean mouseScroll(float px, float py, float amount) {
		if (this.collapsed || !this.contains(px, py)) {
			return false;
		}

		this.scrollTarget -= amount * 24.0F;
		return true;
	}

	boolean capturesKeys() {
		return this.rows.stream().anyMatch(ModuleRow::capturesKeys);
	}

	boolean keyDown(Key key, boolean control, InputBackend input) {
		for (ModuleRow row : this.rows) {
			if (row.capturesKeys()) {
				return row.keyDown(key, control, input);
			}
		}

		return false;
	}

	void charTyped(String text) {
		this.rows.forEach(row -> row.charTyped(text));
	}

	void blur() {
		this.rows.forEach(ModuleRow::blur);
	}
}
