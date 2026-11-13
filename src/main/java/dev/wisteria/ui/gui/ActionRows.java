package dev.wisteria.ui.gui;

import dev.wisteria.platform.InputBackend;
import dev.wisteria.platform.Key;
import dev.wisteria.platform.ScreenBackend.MouseButton;
import dev.wisteria.setting.ActionSetting;
import dev.wisteria.setting.InputSetting;
import dev.wisteria.setting.ListSetting;
import dev.wisteria.setting.PasswordSetting;
import dev.wisteria.ui.Colors;
import dev.wisteria.ui.Metrics.Radius;
import dev.wisteria.ui.Metrics.Stroke;
import dev.wisteria.ui.Metrics.Type;
import dev.wisteria.ui.Theme;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Rows for settings that do something rather than hold a value: buttons, fields with a button, lists, secrets. */
final class ActionRows {
	private static final float BUTTON_HEIGHT = 13.0F;

	private ActionRows() {
	}

	/** A small pill button; registers its control and returns whether it is under the mouse. */
	static Controls.State button(Ui ui, String id, String label, float x, float y, float width, boolean enabled, java.util.function.Supplier<String> state,
		@Nullable String note) {
		Theme theme = ui.theme();
		Controls.State control = ui.controls().register(id, Controls.Kind.BUTTON, x, y, width, BUTTON_HEIGHT, enabled, state, note);
		int fill = !enabled ? Colors.withAlpha(theme.raised(), 0x80) : control.pressed() ? theme.accentPressed() : control.hovered() ? theme.accentHover() : theme.accent();
		ui.rounded(x, y, width, BUTTON_HEIGHT, BUTTON_HEIGHT / 2.0F, fill);
		ui.textInRow(Type.CAPTION, label, x + (width - Type.CAPTION.width(ui.renderer(), label)) / 2.0F, y, BUTTON_HEIGHT,
			enabled ? theme.onAccent() : theme.textDisabled());
		return control;
	}

	static float buttonWidth(Ui ui, String label) {
		return Type.CAPTION.width(ui.renderer(), label) + 14.0F;
	}

	/** A label and a button. */
	static final class Action extends SettingRow {
		private final ActionSetting setting;

		Action(ActionSetting setting) {
			super(setting.name(), setting.description());
			this.setting = setting;
		}

		@Override
		float height() {
			return HEIGHT;
		}

		@Override
		void render(Ui ui) {
			ui.textInRow(Type.CAPTION, this.label(), this.x, this.y, HEIGHT, ui.theme().textMuted());
			float width = buttonWidth(ui, this.setting.button());
			button(ui, this.id, this.setting.button(), this.x + this.width - width, this.y + 1.0F, width, true,
				() -> Integer.toString(this.setting.get()), this.setting.note());
		}

		@Override
		boolean mouseDown(float px, float py, MouseButton button) {
			if (button == MouseButton.LEFT && px >= this.x + this.width - 70.0F) {
				this.setting.press();
			}

			return true;
		}
	}

	/** A label, a field and a button; Enter in the field does the same as the button. */
	static class Input extends SettingRow {
		protected final InputSetting setting;
		protected final TextInput input;
		private float buttonX;

		Input(InputSetting setting) {
			super(setting.name(), setting.description());
			this.setting = setting;
			this.input = new TextInput("", setting.placeholder(), 64).onCommit(text -> {
			});
		}

		@Override
		float height() {
			return 13.0F + BUTTON_HEIGHT + (this.setting.lastError().isEmpty() ? 2.0F : 13.0F);
		}

		@Override
		void render(Ui ui) {
			Theme theme = ui.theme();
			ui.textInRow(Type.CAPTION, this.label(), this.x, this.y, 13.0F, theme.textMuted());
			float width = buttonWidth(ui, this.setting.button());
			this.buttonX = this.x + this.width - width;
			this.input.setId(this.id + ":field");
			this.input.render(ui, this.x, this.y + 13.0F, this.width - width - 4.0F, BUTTON_HEIGHT, Radius.S, Type.CAPTION);
			button(ui, this.id, this.setting.button(), this.buttonX, this.y + 13.0F, width, true, () -> Integer.toString(this.setting.get()), this.buttonNote());
			if (!this.setting.lastError().isEmpty()) {
				ui.textInRow(Type.CAPTION, ui.fit(Type.CAPTION, this.setting.lastError(), this.width), this.x, this.y + 13.0F + BUTTON_HEIGHT, 13.0F, theme.danger());
			}
		}

		protected @Nullable String buttonNote() {
			return null;
		}

		private void submit() {
			if (this.setting.submit(this.input.text())) {
				this.input.setText("");
			}
		}

		@Override
		boolean mouseDown(float px, float py, MouseButton button) {
			if (button != MouseButton.LEFT) {
				return true;
			}

			if (py >= this.y + 13.0F && py < this.y + 13.0F + BUTTON_HEIGHT && px >= this.buttonX) {
				this.input.setFocused(false);
				this.submit();
			} else {
				this.input.setFocused(this.input.contains(px, py));
			}

			return true;
		}

		@Override
		boolean capturesKeys() {
			return this.input.isFocused();
		}

		@Override
		boolean keyDown(Key key, boolean control, InputBackend input) {
			if ((key == Key.ENTER || key == Key.NUMPAD_ENTER) && this.input.isFocused()) {
				this.input.setFocused(false);
				this.submit();
				return true;
			}

			return this.input.keyDown(key, control, input);
		}

		@Override
		void charTyped(String text) {
			this.input.type(text);
		}

		@Override
		void blur() {
			this.input.setFocused(false);
		}
	}

	/** Field and Add, then the entries, each with a remove button. */
	static final class Entries extends Input {
		private static final float ENTRY = 13.0F;
		private final ListSetting list;
		private List<String> shown = List.of();

		Entries(ListSetting list) {
			super(list);
			this.list = list;
		}

		@Override
		float height() {
			return super.height() + Math.max(1, this.list.entries().size()) * ENTRY;
		}

		@Override
		void render(Ui ui) {
			super.render(ui);
			Theme theme = ui.theme();
			this.shown = this.list.entries();
			float top = this.y + super.height();
			if (this.shown.isEmpty()) {
				ui.textInRow(Type.CAPTION, "(empty)", this.x + 4.0F, top, ENTRY, theme.textDisabled());
				return;
			}

			for (int i = 0; i < this.shown.size(); i++) {
				String entry = this.shown.get(i);
				float ey = top + i * ENTRY;
				ui.textInRow(Type.CAPTION, ui.fit(Type.CAPTION, entry, this.width - 18.0F), this.x + 4.0F, ey, ENTRY, theme.text());
				Controls.State remove = ui.controls().register(this.id + ":remove:" + entry, Controls.Kind.BUTTON, this.x + this.width - 13.0F, ey, 13.0F, ENTRY,
					true, () -> String.join("|", this.list.entries()), "removes the entry; covered by its own test");
				int colour = remove.hovered() ? theme.danger() : theme.textMuted();
				ui.rect(this.x + this.width - 9.5F, ey + ENTRY / 2.0F - 0.5F, 6.0F, 1.0F, colour);
			}
		}

		@Override
		boolean mouseDown(float px, float py, MouseButton button) {
			float top = this.y + super.height();
			if (button == MouseButton.LEFT && py >= top && px >= this.x + this.width - 13.0F) {
				int index = (int)((py - top) / ENTRY);
				if (index >= 0 && index < this.shown.size()) {
					this.list.remove(this.shown.get(index));
				}

				return true;
			}

			return super.mouseDown(px, py, button);
		}
	}

	/** A masked field: what is typed goes to the secrets file on Enter or when focus leaves, and is never shown again. */
	static final class Secret extends SettingRow {
		private final PasswordSetting setting;
		private final TextInput input;

		Secret(PasswordSetting setting) {
			super(setting.name(), setting.description());
			this.setting = setting;
			this.input = new TextInput("", "type, then Enter", 128).masked().onCommit(text -> {
				if (!text.isEmpty()) {
					try {
						setting.store(text);
					} catch (IllegalArgumentException ignored) {
						// no proxy chosen: nothing stored, the field just empties
					}
				}

				this.clear();
			});
		}

		private void clear() {
			this.input.setText("");
		}

		@Override
		float height() {
			return 29.0F;
		}

		@Override
		void render(Ui ui) {
			Theme theme = ui.theme();
			ui.textInRow(Type.CAPTION, this.label(), this.x, this.y, 13.0F, theme.textMuted());
			ui.textRightInRow(Type.CAPTION, this.setting.isStored() ? "set" : "not set", this.x + this.width, this.y, 13.0F,
				this.setting.isStored() ? theme.success() : theme.textDisabled());
			this.input.setId(this.id);
			this.input.render(ui, this.x, this.y + 13.0F, this.width, 13.0F, Radius.S, Type.CAPTION);
		}

		@Override
		boolean mouseDown(float px, float py, MouseButton button) {
			this.input.setFocused(button == MouseButton.LEFT && this.input.contains(px, py));
			return true;
		}

		@Override
		boolean capturesKeys() {
			return this.input.isFocused();
		}

		@Override
		boolean keyDown(Key key, boolean control, InputBackend input) {
			return this.input.keyDown(key, control, input);
		}

		@Override
		void charTyped(String text) {
			this.input.type(text);
		}

		@Override
		void blur() {
			this.input.setFocused(false);
		}
	}

	/** A label and a live value; nothing to click. */
	static final class Info extends SettingRow {
		private final dev.wisteria.setting.InfoSetting setting;

		Info(dev.wisteria.setting.InfoSetting setting) {
			super(setting.name(), setting.description());
			this.setting = setting;
		}

		@Override
		float height() {
			return HEIGHT;
		}

		@Override
		void render(Ui ui) {
			ui.textInRow(Type.CAPTION, this.label(), this.x, this.y, HEIGHT, ui.theme().textMuted());
			ui.textRightInRow(Type.MONO, this.setting.format(), this.x + this.width, this.y, HEIGHT, ui.theme().text());
		}
	}
}
