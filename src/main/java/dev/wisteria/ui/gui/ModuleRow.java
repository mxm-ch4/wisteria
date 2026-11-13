package dev.wisteria.ui.gui;

import dev.wisteria.Client;
import dev.wisteria.platform.InputBackend;
import dev.wisteria.platform.Key;
import dev.wisteria.platform.ScreenBackend.MouseButton;
import dev.wisteria.module.Module;
import dev.wisteria.modules.clickgui.ClickGui;
import dev.wisteria.setting.Setting;
import dev.wisteria.ui.Animation;
import dev.wisteria.ui.Colors;
import dev.wisteria.ui.Easing;
import dev.wisteria.ui.Metrics.Radius;
import dev.wisteria.ui.Metrics.Type;
import dev.wisteria.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A module in a category panel. Click the name to toggle it; click the arrow, or right-click anywhere on the
 * row, to unfold its bind and settings.
 */
final class ModuleRow {
	static final float HEIGHT = 17.0F;
	/** Width of the arrow's click zone at the right end of the row. */
	static final float ARROW = 18.0F;
	private static final float INSET = 9.0F;

	private final Client client;
	private final Module module;
	private final String path;
	private final Consumer<String> hint;
	private final List<SettingRow> rows = new ArrayList<>();
	private final Animation expand = new Animation(0.0F, Animation.NORMAL, Easing.OUT_CUBIC);
	private final Animation enabled;
	private final Animation hover = new Animation(0.0F, Animation.FAST, Easing.OUT_CUBIC);
	private boolean expanded;
	private float x;
	private float y;
	private float width;

	ModuleRow(Client client, Module module, Consumer<String> hint) {
		this.client = client;
		this.module = module;
		this.path = module.category().name().toLowerCase(Locale.ROOT) + "/" + module.id();
		this.hint = hint;
		this.enabled = new Animation(module.isEnabled() ? 1.0F : 0.0F, Animation.NORMAL, Easing.OUT_CUBIC);
		SettingRow bind = new SettingRows.KeyBinder(module.bind());
		bind.assignId("setting:" + this.path + "/bind");
		this.rows.add(bind);
		for (Setting<?> setting : module.settings()) {
			SettingRow row = SettingRows.of(setting);
			if (row != null) {
				row.assignId("setting:" + this.path + "/" + setting.id());
				this.rows.add(row);
			}
		}
	}

	Module module() {
		return this.module;
	}

	boolean isExpanded() {
		return this.expanded;
	}

	void setExpanded(boolean expanded) {
		this.expanded = expanded;
		if (!expanded) {
			this.blur();
		}
	}

	private float settingsHeight() {
		float total = 4.0F;
		for (SettingRow row : this.rows) {
			total += row.height();
		}

		return total + 3.0F;
	}

	float height() {
		return HEIGHT + this.expand.value() * this.settingsHeight();
	}

	void render(Ui ui, float x, float y, float width) {
		this.x = x;
		this.y = y;
		this.width = width;
		Theme theme = ui.theme();
		Controls controls = ui.controls();
		// Toggling the ClickGUI from inside it closes the very screen it is drawn on.
		// Modules whose "on" is an open screen (ClickGUI, HUD Editor) swap this screen out; they have their own tests.
		String note = this.module instanceof ClickGui ? "switching ClickGUI off closes this screen; covered by the Right Shift test"
			: !this.module.savesEnabled() ? "opens another screen in place of this one; covered by its own test" : null;
		Controls.State row = controls.register("module:" + this.path, Controls.Kind.TOGGLE, x + 3.0F, y + 1.0F, width - 6.0F - ARROW, HEIGHT - 2.0F,
			true, () -> this.module.isEnabled() ? "on" : "off", note);
		Controls.State arrow = controls.register("expand:" + this.path, Controls.Kind.EXPANDER, x + width - 3.0F - ARROW, y + 1.0F, ARROW, HEIGHT - 2.0F,
			() -> this.expanded ? "open" : "closed");
		boolean hovered = row.hovered() || arrow.hovered();
		if (hovered) {
			this.hint.accept(this.module.description());
		}

		this.hover.animateTo(hovered);
		this.enabled.animateTo(this.module.isEnabled());
		this.expand.animateTo(this.expanded);
		float on = this.enabled.value();
		float reveal = this.expand.value();

		int background = Colors.mix(Colors.withAlpha(theme.hover(), Math.round(255.0F * this.hover.value())), Colors.withAlpha(theme.accent(), 46), on);
		if (row.pressed() || arrow.pressed()) {
			background = Colors.withAlpha(Colors.darken(theme.hover(), 0.25F), 255);
		}

		ui.rounded(x + 3.0F, y + 1.0F, width - 6.0F, HEIGHT - 2.0F, Radius.M, background);
		ui.rounded(x + 6.0F, y + 5.0F + (1.0F - on) * 3.5F, 2.0F, 7.0F * on, 1.0F, theme.accent());
		ui.textInRow(Type.BODY, ui.fit(Type.BODY, this.module.name(), width - 20.0F - ARROW), x + 12.0F, y, HEIGHT,
			Colors.mix(theme.text(), theme.accent(), on));
		if (arrow.hovered() || arrow.pressed()) {
			ui.rounded(x + width - 3.0F - ARROW + 2.0F, y + 3.0F, ARROW - 4.0F, HEIGHT - 6.0F, Radius.S, Ui.stateColor(theme.raised(), arrow));
		}

		ui.chevron(x + width - 3.0F - ARROW / 2.0F, y + HEIGHT / 2.0F, 4.0F, reveal * (float)(Math.PI / 2.0),
			arrow.hovered() ? theme.text() : Colors.mix(theme.textDisabled(), theme.textMuted(), this.hover.value()));

		if (reveal <= 0.01F) {
			return;
		}

		// The settings unfold underneath; the caller's scissor trims whatever has not arrived yet.
		Ui inner = ui.faded(reveal);
		float areaHeight = this.settingsHeight() * reveal;
		inner.rounded(x + 3.0F, y + HEIGHT, width - 6.0F, Math.max(0.0F, areaHeight - 2.0F), Radius.M, Colors.withAlpha(theme.background(), 140));
		ui.renderer().pushScissor(x, y + HEIGHT, width, areaHeight);
		controls.pushClip(x, y + HEIGHT, width, areaHeight);
		controls.pushGroup("expand:" + this.path);
		float rowY = y + HEIGHT + 4.0F - (1.0F - reveal) * 6.0F;
		for (SettingRow setting : this.rows) {
			setting.layout(x + INSET, rowY, width - 2.0F * INSET);
			if (this.expanded) {
				setting.render(inner);
			} else {
				// Folding away: drawn, but no longer clickable.
				controls.pushClip(0.0F, 0.0F, 0.0F, 0.0F);
				setting.render(inner);
				controls.popClip();
			}

			if (this.expanded && setting.contains(ui.mouseX(), ui.mouseY()) && (row.hovered() || ui.hovering(x, y + HEIGHT, width, areaHeight))) {
				this.hint.accept(setting.label() + ": " + setting.description());
			}

			rowY += setting.height();
		}

		controls.popGroup();
		controls.popClip();
		ui.renderer().popScissor();
	}

	boolean mouseDown(float px, float py, MouseButton button) {
		if (!Ui.inside(px, py, this.x, this.y, this.width, this.height())) {
			return false;
		}

		if (py < this.y + HEIGHT) {
			boolean onArrow = px >= this.x + this.width - 3.0F - ARROW;
			if (button == MouseButton.RIGHT || button == MouseButton.LEFT && onArrow) {
				this.setExpanded(!this.expanded);
			} else if (button == MouseButton.LEFT) {
				this.client.setEnabled(this.module, !this.module.isEnabled());
			}

			return true;
		}

		if (this.expanded) {
			for (SettingRow row : this.rows) {
				if (row.contains(px, py)) {
					return row.mouseDown(px, py, button);
				}
			}
		}

		return true;
	}

	void mouseUp() {
		this.rows.forEach(SettingRow::mouseUp);
	}

	boolean capturesKeys() {
		return this.rows.stream().anyMatch(SettingRow::capturesKeys);
	}

	boolean keyDown(Key key, boolean control, InputBackend input) {
		for (SettingRow row : this.rows) {
			if (row.capturesKeys()) {
				return row.keyDown(key, control, input);
			}
		}

		return false;
	}

	void charTyped(String text) {
		for (SettingRow row : this.rows) {
			if (row.capturesKeys()) {
				row.charTyped(text);
			}
		}
	}

	void blur() {
		this.rows.forEach(SettingRow::blur);
	}

	/**
	 * Ends focus, recording and open lists everywhere except under the click. Blurring the clicked row too
	 * would close a dropdown before the click on its option arrives.
	 */
	void blurExcept(float px, float py) {
		for (SettingRow row : this.rows) {
			if (!this.expanded || !row.contains(px, py)) {
				row.blur();
			}
		}
	}
}
