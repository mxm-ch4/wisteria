package dev.wisteria.ui.gui;

import dev.wisteria.Client;
import dev.wisteria.platform.Key;
import dev.wisteria.render.Renderer2D;
import dev.wisteria.platform.ScreenBackend;
import dev.wisteria.platform.ScreenBackend.MouseButton;
import dev.wisteria.module.Category;
import dev.wisteria.module.Module;
import dev.wisteria.ui.Animation;
import dev.wisteria.ui.Colors;
import dev.wisteria.ui.Easing;
import dev.wisteria.ui.Metrics.Type;
import dev.wisteria.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * The module browser: one draggable panel per category, a search box, and a theme switcher. Built only
 * from the design system; see docs/design-system.md.
 */
public final class ClickGuiScreen implements ScreenBackend.ClientScreen {
	private static final float SEARCH_WIDTH = 150.0F;
	private static final float SEARCH_HEIGHT = 16.0F;
	private static final float DOT = 9.0F;

	private final Client client;
	private final Runnable onClosed;
	private final List<Panel> panels = new ArrayList<>();
	private final TextInput search = new TextInput("search", "Search modules", 32);
	private final Controls controls = new Controls();
	private final Animation open = new Animation(0.0F, Animation.SLOW, Easing.OUT_QUINT);
	private String hint = "";
	private String shownHint = "";
	private float width;
	private float height;
	private double renderMicros;

	/** @param onClosed run when the screen goes away, however that happens */
	public ClickGuiScreen(Client client, Runnable onClosed) {
		this.client = client;
		this.onClosed = onClosed;
		this.open.animateTo(1.0F);
	}

	@Override
	public String title() {
		return "Wisteria";
	}

	/** Panels are created on the first frame, when the screen size is known for the default layout. */
	private void build(float screenWidth) {
		float x = 8.0F;
		float y = 32.0F;
		for (Category category : Category.values()) {
			List<Module> modules = this.client.modules().in(category);
			if (modules.isEmpty()) {
				continue;
			}

			if (x + Panel.WIDTH > screenWidth - 4.0F && x > 8.0F) {
				x = 8.0F;
				y += 120.0F;
			}

			GuiState.PanelState saved = this.client.gui().panel(category);
			this.panels.add(saved != null
				? new Panel(this.client, category, modules, saved.x(), saved.y(), saved.collapsed(), this::hint)
				: new Panel(this.client, category, modules, x, y, false, this::hint));
			x += Panel.WIDTH + 6.0F;
		}
	}

	private void hint(String text) {
		this.hint = text;
	}

	private Predicate<Module> filter() {
		String query = this.search.text().trim().toLowerCase(Locale.ROOT);
		return query.isEmpty() ? module -> true : module -> module.name().toLowerCase(Locale.ROOT).contains(query);
	}

	@Override
	public void render(Renderer2D renderer, float mouseX, float mouseY, float tickDelta) {
		long started = System.nanoTime();
		this.width = renderer.width();
		this.height = renderer.height();
		if (this.panels.isEmpty()) {
			this.build(this.width);
		}

		Theme theme = this.client.themes().current();
		float appear = this.open.value();
		this.controls.begin(mouseX, mouseY);
		Ui ui = new Ui(renderer, theme, mouseX, mouseY, appear, this.controls);
		ui.rect(0.0F, 0.0F, this.width, this.height, Colors.withAlpha(theme.background(), 120));

		// Everything slides up a little as it fades in.
		renderer.push();
		renderer.translate(0.0F, (1.0F - appear) * 8.0F);

		this.search.render(ui, this.searchX(), 8.0F, SEARCH_WIDTH, SEARCH_HEIGHT, SEARCH_HEIGHT / 2.0F, Type.BODY);

		this.hint = "";
		Predicate<Module> filter = this.filter();
		for (Panel panel : this.panels) {
			panel.render(ui, filter);
		}

		// Status line: what the thing under the cursor does. The last hint stays while it fades out.
		if (!this.hint.isEmpty()) {
			this.shownHint = this.hint;
		}

		float barY = this.height - 15.0F;
		ui.textInRow(Type.CAPTION, ui.fit(Type.CAPTION, this.shownHint, this.width - 110.0F), 8.0F, barY, 12.0F,
			this.hint.isEmpty() ? Colors.withAlpha(theme.textMuted(), 0) : theme.textMuted());

		// Theme presets as dots in their accent colour; the active one has a ring.
		List<String> presets = this.client.themes().presetIds();
		for (int i = 0; i < presets.size(); i++) {
			Theme preset = this.client.themes().preset(presets.get(i));
			float dx = this.dotX(i, presets.size());
			String presetId = presets.get(i);
			boolean active = presetId.equals(this.client.themes().presetId()) && this.client.themes().customAccent() == null;
			// The active preset's dot does nothing when clicked, so it counts as disabled; it wears a ring instead of fading.
			Controls.State dot = this.controls.register("theme:" + presetId, Controls.Kind.BUTTON, dx - 2.0F, barY - 1.0F, DOT + 4.0F, DOT + 4.0F,
				!active, () -> this.client.themes().presetId() + "/" + this.client.themes().customAccent(), null);
			if (dot.hovered()) {
				this.shownHint = "Theme: " + preset.name();
				ui.shadow(dx, barY + 1.0F, DOT, DOT, DOT / 2.0F, 4.0F, Colors.withAlpha(preset.accent(), 170));
			}

			float size = dot.pressed() ? DOT - 2.0F : DOT;
			float inset = (DOT - size) / 2.0F;
			ui.rounded(dx + inset, barY + 1.0F + inset, size, size, size / 2.0F, preset.accent());
			if (active) {
				ui.outline(dx - 2.0F, barY - 1.0F, DOT + 4.0F, DOT + 4.0F, (DOT + 4.0F) / 2.0F, 1.0F, theme.text());
			}
		}

		renderer.pop();
		this.controls.end();
		this.renderMicros += ((System.nanoTime() - started) / 1000.0 - this.renderMicros) * 0.1;
	}

	private float searchX() {
		return (this.width - SEARCH_WIDTH) / 2.0F;
	}

	private float dotX(int index, int count) {
		return this.width - 10.0F - (count - index) * (DOT + 6.0F);
	}

	/** The topmost panel under a point: the last one drawn wins. */
	private Panel panelAt(float px, float py) {
		for (int i = this.panels.size() - 1; i >= 0; i--) {
			if (this.panels.get(i).contains(px, py)) {
				return this.panels.get(i);
			}
		}

		return null;
	}

	@Override
	public boolean mouseDown(float x, float y, MouseButton button) {
		// A click ends whatever was being typed, recorded or left open elsewhere; the thing clicked keeps its state.
		this.controls.press(x, y);
		this.panels.forEach(panel -> panel.blurExcept(x, y));
		this.search.setFocused(button == MouseButton.LEFT && this.search.contains(x, y));
		if (this.search.isFocused()) {
			return true;
		}

		float barY = this.height - 15.0F;
		List<String> presets = this.client.themes().presetIds();
		for (int i = 0; i < presets.size(); i++) {
			if (button == MouseButton.LEFT && Ui.inside(x, y, this.dotX(i, presets.size()) - 2.0F, barY - 1.0F, DOT + 4.0F, DOT + 4.0F)) {
				this.client.themes().usePreset(presets.get(i));
				return true;
			}
		}

		Panel panel = this.panelAt(x, y);
		if (panel == null) {
			return false;
		}

		this.panels.remove(panel);
		this.panels.add(panel);
		return panel.mouseDown(x, y, button, this.filter());
	}

	@Override
	public void mouseUp(float x, float y, MouseButton button) {
		this.controls.release();
		for (Panel panel : this.panels) {
			boolean wasDragging = panel.isDragging();
			panel.mouseUp();
			if (wasDragging) {
				this.remember(panel);
			}
		}
	}

	@Override
	public boolean mouseScroll(float x, float y, float amount) {
		Panel panel = this.panelAt(x, y);
		return panel != null && panel.mouseScroll(x, y, amount);
	}

	@Override
	public boolean keyDown(Key key, boolean shift, boolean control, boolean alt) {
		for (Panel panel : this.panels) {
			if (panel.capturesKeys()) {
				return panel.keyDown(key, control, this.client.platform().input());
			}
		}

		if (this.search.isFocused()) {
			return this.search.keyDown(key, control, this.client.platform().input());
		}

		// The key that opened the GUI closes it again; Escape is left to the game, which closes any screen.
		Module gui = this.client.modules().get("clickgui");
		if (gui != null && gui.bind().matches(key)) {
			this.client.platform().screens().close();
			return true;
		}

		return false;
	}

	@Override
	public void charTyped(String text) {
		for (Panel panel : this.panels) {
			if (panel.capturesKeys()) {
				panel.charTyped(text);
				return;
			}
		}

		// Just start typing to search.
		this.search.setFocused(true);
		this.search.type(text);
	}

	@Override
	public void onClose() {
		this.panels.forEach(this::remember);
		this.onClosed.run();
	}

	private void remember(Panel panel) {
		this.client.gui().setPanel(panel.category(), new GuiState.PanelState(panel.x(), panel.y(), panel.isCollapsed()));
	}

	/** Smoothed time the screen takes to lay out and submit one frame, in microseconds. */
	public double renderMicros() {
		return this.renderMicros;
	}

	/** The controls of the last frame: for diagnostics and the click-everything tests. */
	public Controls controls() {
		return this.controls;
	}

	// For tests.
	List<Panel> panels() {
		return this.panels;
	}

	TextInput search() {
		return this.search;
	}
}
