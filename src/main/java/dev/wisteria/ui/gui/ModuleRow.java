package dev.wisteria.ui.gui;

import dev.wisteria.Client;
import dev.wisteria.platform.InputBackend;
import dev.wisteria.platform.Key;
import dev.wisteria.platform.ScreenBackend.MouseButton;
import dev.wisteria.module.Module;
import dev.wisteria.setting.Setting;
import dev.wisteria.ui.Animation;
import dev.wisteria.ui.Colors;
import dev.wisteria.ui.Easing;
import dev.wisteria.ui.Metrics.Radius;
import dev.wisteria.ui.Metrics.Type;
import dev.wisteria.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A module in a category panel: click to toggle, right-click to unfold its bind and settings. */
final class ModuleRow {
	static final float HEIGHT = 17.0F;
	private static final float INSET = 9.0F;

	private final Client client;
	private final Module module;
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
		this.hint = hint;
		this.enabled = new Animation(module.isEnabled() ? 1.0F : 0.0F, Animation.NORMAL, Easing.OUT_CUBIC);
		this.rows.add(new SettingRows.KeyBinder(module.bind()));
		for (Setting<?> setting : module.settings()) {
			SettingRow row = SettingRows.of(setting);
			if (row != null) {
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

	void render(Ui ui, float x, float y, float width, boolean interactive) {
		this.x = x;
		this.y = y;
		this.width = width;
		Theme theme = ui.theme();
		boolean hovered = interactive && ui.hovering(x, y, width, HEIGHT);
		if (hovered) {
			this.hint.accept(this.module.description());
		}

		this.hover.animateTo(hovered);
		this.enabled.animateTo(this.module.isEnabled());
		this.expand.animateTo(this.expanded);
		float on = this.enabled.value();
		float reveal = this.expand.value();

		ui.rounded(x + 3.0F, y + 1.0F, width - 6.0F, HEIGHT - 2.0F, Radius.M,
			Colors.mix(Colors.withAlpha(theme.hover(), Math.round(255.0F * this.hover.value())), Colors.withAlpha(theme.accent(), 46), on));
		ui.rounded(x + 6.0F, y + 5.0F + (1.0F - on) * 3.5F, 2.0F, 7.0F * on, 1.0F, theme.accent());
		ui.textInRow(Type.BODY, this.module.name(), x + 12.0F, y, HEIGHT, Colors.mix(theme.text(), theme.accent(), on));
		ui.chevron(x + width - 10.0F, y + HEIGHT / 2.0F, 4.0F, reveal * (float)(Math.PI / 2.0), Colors.mix(theme.textDisabled(), theme.textMuted(), this.hover.value()));

		if (reveal <= 0.01F) {
			return;
		}

		// The settings unfold underneath; the caller's scissor trims whatever has not arrived yet.
		Ui inner = new Ui(ui.renderer(), theme, ui.mouseX(), ui.mouseY(), ui.opacity() * reveal);
		float areaHeight = this.settingsHeight() * reveal;
		inner.rounded(x + 3.0F, y + HEIGHT, width - 6.0F, Math.max(0.0F, areaHeight - 2.0F), Radius.M, Colors.withAlpha(theme.background(), 140));
		ui.renderer().pushScissor(x, y + HEIGHT, width, areaHeight);
		float rowY = y + HEIGHT + 4.0F - (1.0F - reveal) * 6.0F;
		for (SettingRow row : this.rows) {
			row.layout(x + INSET, rowY, width - 2.0F * INSET);
			row.render(inner);
			if (interactive && this.expanded && row.contains(ui.mouseX(), ui.mouseY())) {
				this.hint.accept(row.label() + ": " + row.description());
			}

			rowY += row.height();
		}

		ui.renderer().popScissor();
	}

	boolean mouseDown(float px, float py, MouseButton button) {
		if (!Ui.inside(px, py, this.x, this.y, this.width, this.height())) {
			return false;
		}

		if (py < this.y + HEIGHT) {
			if (button == MouseButton.LEFT) {
				this.client.setEnabled(this.module, !this.module.isEnabled());
			} else if (button == MouseButton.RIGHT) {
				this.setExpanded(!this.expanded);
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
}
