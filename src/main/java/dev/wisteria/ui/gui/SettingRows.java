package dev.wisteria.ui.gui;

import dev.wisteria.platform.InputBackend;
import dev.wisteria.platform.Key;
import dev.wisteria.platform.ScreenBackend.MouseButton;
import dev.wisteria.setting.BoolSetting;
import dev.wisteria.setting.ColorSetting;
import dev.wisteria.setting.EnumSetting;
import dev.wisteria.setting.KeySetting;
import dev.wisteria.setting.NumberSetting;
import dev.wisteria.setting.Setting;
import dev.wisteria.setting.StringSetting;
import dev.wisteria.ui.Animation;
import dev.wisteria.ui.Colors;
import dev.wisteria.ui.Easing;
import dev.wisteria.ui.Metrics.Radius;
import dev.wisteria.ui.Metrics.Stroke;
import dev.wisteria.ui.Metrics.Type;
import dev.wisteria.ui.Theme;
import java.util.List;
import java.util.Optional;

/** The control for each kind of {@link Setting}. */
final class SettingRows {
	private SettingRows() {
	}

	/** @return the row for a setting, or null for a kind the GUI does not know yet */
	static SettingRow of(Setting<?> setting) {
		if (setting instanceof dev.wisteria.setting.ListSetting list) {
			return new ActionRows.Entries(list);
		}

		if (setting instanceof dev.wisteria.setting.InputSetting input) {
			return new ActionRows.Input(input);
		}

		if (setting instanceof dev.wisteria.setting.ActionSetting action) {
			return new ActionRows.Action(action);
		}

		if (setting instanceof dev.wisteria.setting.PasswordSetting password) {
			return new ActionRows.Secret(password);
		}

		if (setting instanceof dev.wisteria.setting.InfoSetting info) {
			return new ActionRows.Info(info);
		}

		if (setting instanceof dev.wisteria.setting.ChoiceSetting choice) {
			return new Dropdown(choice);
		}

		if (setting instanceof BoolSetting bool) {
			return new Toggle(bool);
		}

		if (setting instanceof NumberSetting<?> number) {
			return new Slider(number);
		}

		if (setting instanceof EnumSetting<?> choice) {
			return new Dropdown(choice);
		}

		if (setting instanceof ColorSetting colour) {
			return new ColourPicker(colour);
		}

		if (setting instanceof KeySetting key) {
			return new KeyBinder(key);
		}

		return setting instanceof StringSetting string ? new TextField(string) : null;
	}

	static final class Toggle extends SettingRow {
		private final BoolSetting setting;
		private final Animation state;

		Toggle(BoolSetting setting) {
			super(setting.name(), setting.description());
			this.setting = setting;
			this.state = new Animation(setting.get() ? 1.0F : 0.0F, Animation.NORMAL, Easing.OUT_BACK);
		}

		@Override
		float height() {
			return HEIGHT;
		}

		@Override
		void render(Ui ui) {
			Theme theme = ui.theme();
			Controls.State control = ui.controls().register(this.id, Controls.Kind.TOGGLE, this.x, this.y, this.width, HEIGHT, this.setting::format);
			this.state.animateTo(this.setting.get());
			float on = this.state.value();
			float settled = Math.max(0.0F, Math.min(1.0F, on));
			ui.textInRow(Type.CAPTION, this.label(), this.x, this.y, HEIGHT,
				control.hovered() ? theme.text() : Colors.mix(theme.textMuted(), theme.text(), settled));
			float tx = this.x + this.width - 18.0F;
			float ty = this.y + 3.0F;
			ui.rounded(tx, ty, 18.0F, 9.0F, 4.5F, Ui.stateColor(Colors.mix(theme.raised(), theme.accent(), settled), control));
			ui.outline(tx, ty, 18.0F, 9.0F, 4.5F, Stroke.HAIRLINE, Colors.mix(control.hovered() ? theme.textMuted() : theme.outline(), theme.accent(), settled));
			float knob = control.pressed() ? 4.0F : 5.0F;
			float inset = (5.0F - knob) / 2.0F;
			ui.rounded(tx + 2.0F + inset + on * 9.0F, ty + 2.0F + inset, knob, knob, knob / 2.0F, Colors.mix(theme.textMuted(), theme.onAccent(), settled));
		}

		@Override
		boolean mouseDown(float px, float py, MouseButton button) {
			if (button != MouseButton.LEFT) {
				return false;
			}

			this.setting.set(!this.setting.get());
			return true;
		}
	}

	static final class Slider extends SettingRow {
		private static final float SLIDER_HEIGHT = 24.0F;
		private final NumberSetting<?> setting;
		private final Animation shown;
		private boolean dragging;

		Slider(NumberSetting<?> setting) {
			super(setting.name(), setting.description());
			this.setting = setting;
			this.shown = new Animation((float)setting.fraction(), Animation.FAST, Easing.OUT_CUBIC);
		}

		@Override
		float height() {
			return SLIDER_HEIGHT;
		}

		private void follow(float px) {
			this.setting.setFraction((px - this.x) / this.width);
		}

		@Override
		void render(Ui ui) {
			Theme theme = ui.theme();
			if (this.dragging) {
				this.follow(ui.mouseX());
				this.shown.snapTo((float)this.setting.fraction());
			} else {
				this.shown.animateTo((float)this.setting.fraction());
			}

			float fraction = this.shown.value();
			Controls.State control = ui.controls().register(this.id, Controls.Kind.SLIDER, this.x, this.y, this.width, SLIDER_HEIGHT, this.setting::format);
			ui.textInRow(Type.CAPTION, this.label(), this.x, this.y, 14.0F, control.hovered() || this.dragging ? theme.text() : theme.textMuted());
			ui.textRightInRow(Type.MONO, this.setting.format(), this.x + this.width, this.y, 14.0F, theme.text());
			float ty = this.y + 16.0F;
			ui.rounded(this.x, ty, this.width, 3.0F, 1.5F, theme.raised());
			ui.horizontalGradient(this.x, ty, this.width * fraction, 3.0F, 1.5F, theme.accentPressed(), theme.accent());
			float thumbX = this.x + this.width * fraction;
			if (this.dragging || control.hovered()) {
				ui.shadow(thumbX - 3.5F, ty - 2.0F, 7.0F, 7.0F, 3.5F, this.dragging ? 6.0F : 4.0F, Colors.withAlpha(theme.accent(), this.dragging ? 200 : 140));
			}

			float thumb = this.dragging ? 8.0F : 7.0F;
			ui.rounded(thumbX - thumb / 2.0F, ty + 1.5F - thumb / 2.0F, thumb, thumb, thumb / 2.0F, theme.text());
		}

		@Override
		boolean mouseDown(float px, float py, MouseButton button) {
			if (button == MouseButton.RIGHT) {
				this.setting.reset();
				return true;
			}

			if (button != MouseButton.LEFT) {
				return false;
			}

			this.dragging = true;
			this.follow(px);
			return true;
		}

		@Override
		void mouseUp() {
			this.dragging = false;
		}

		@Override
		void blur() {
			this.dragging = false;
		}
	}

	static final class Dropdown extends SettingRow {
		private static final float OPTION_HEIGHT = 13.0F;
		private final Setting<?> setting;
		private List<String> options;
		private final Animation open = new Animation(0.0F, Animation.NORMAL, Easing.OUT_CUBIC);
		private boolean opened;

		// Options are read again every frame: a choice of proxies or profiles changes while the menu is open.
		Dropdown(Setting<?> setting) {
			super(setting.name(), setting.description());
			this.setting = setting;
			this.options = setting.suggestions();
		}

		@Override
		float height() {
			return HEIGHT + this.open.value() * this.options.size() * OPTION_HEIGHT;
		}

		@Override
		void render(Ui ui) {
			this.options = this.setting.suggestions();
			Theme theme = ui.theme();
			Controls.State control = ui.controls().register(this.id, Controls.Kind.DROPDOWN, this.x, this.y, this.width, HEIGHT,
				() -> this.opened ? "open" : "closed");
			this.open.animateTo(this.opened);
			float reveal = this.open.value();
			if (control.hovered() || control.pressed()) {
				ui.rounded(this.x - 2.0F, this.y, this.width + 4.0F, HEIGHT, Radius.S, Ui.stateColor(theme.hover(), control));
			}

			ui.textInRow(Type.CAPTION, this.label(), this.x, this.y, HEIGHT, control.hovered() ? theme.text() : theme.textMuted());
			float right = this.x + this.width;
			ui.chevron(right - 4.0F, this.y + HEIGHT / 2.0F, 4.0F, reveal * (float)(Math.PI / 2.0), control.hovered() ? theme.text() : theme.textMuted());
			ui.textRightInRow(Type.CAPTION, this.setting.format(), right - 11.0F, this.y, HEIGHT, theme.text());
			if (reveal <= 0.01F) {
				return;
			}

			Ui list = ui.faded(reveal);
			String current = this.setting.format();
			ui.controls().pushGroup(this.id);
			for (int i = 0; i < this.options.size(); i++) {
				float oy = this.y + HEIGHT + i * OPTION_HEIGHT * reveal;
				String option = this.options.get(i);
				boolean selected = option.equals(current);
				Controls.State optionState = this.opened
					? ui.controls().register(this.id + ":" + option, Controls.Kind.OPTION, this.x - 2.0F, oy, this.width + 4.0F, OPTION_HEIGHT * reveal,
						() -> option.equals(this.setting.format()) ? "selected" : "")
					: Controls.State.IDLE;
				if (optionState.hovered() || optionState.pressed()) {
					list.rounded(this.x - 2.0F, oy, this.width + 4.0F, OPTION_HEIGHT, Radius.S, Ui.stateColor(theme.hover(), optionState));
				}

				if (selected) {
					list.rounded(this.x + 2.0F, oy + 5.0F, 3.0F, 3.0F, 1.5F, theme.accent());
				}

				list.textInRow(Type.CAPTION, option, this.x + 9.0F, oy, OPTION_HEIGHT, selected ? theme.accent() : theme.text());
			}

			ui.controls().popGroup();
		}

		@Override
		boolean mouseDown(float px, float py, MouseButton button) {
			if (button != MouseButton.LEFT) {
				return false;
			}

			if (this.opened && py >= this.y + HEIGHT) {
				int index = (int)((py - this.y - HEIGHT) / OPTION_HEIGHT);
				if (index >= 0 && index < this.options.size()) {
					this.setting.parse(this.options.get(index));
				}
			}

			this.opened = !this.opened;
			return true;
		}

		@Override
		void blur() {
			this.opened = false;
		}
	}

	/** HSV picker: saturation/value square, hue bar, alpha bar and a hex field. */
	static final class ColourPicker extends SettingRow {
		private static final float SQUARE = 44.0F;
		private static final float BAR = 7.0F;
		private static final float GAP = 4.0F;
		private static final float FIELD = 13.0F;
		private static final float PICKER = GAP + SQUARE + GAP + BAR + GAP + BAR + GAP + FIELD + GAP;

		private enum Part {
			NONE, SQUARE, HUE, ALPHA
		}

		private final ColorSetting setting;
		private final Animation open = new Animation(0.0F, Animation.NORMAL, Easing.OUT_CUBIC);
		private final TextInput hex;
		private boolean opened;
		private Part dragging = Part.NONE;
		// Kept separately from the colour: hue must not be lost when saturation or value reach zero.
		private float hue;
		private float saturation;
		private float value;
		private int synced;

		ColourPicker(ColorSetting setting) {
			super(setting.name(), setting.description());
			this.setting = setting;
			this.hex = new TextInput("", "#AARRGGBB", 9).onCommit(text -> {
				try {
					this.setting.parse(text);
				} catch (IllegalArgumentException ignored) {
					// not a colour: the field goes back to the current value on the next frame
				}
			});
			this.sync();
		}

		private void sync() {
			float[] hsv = Colors.toHsv(this.setting.get());
			if (hsv[1] > 0.0F && hsv[2] > 0.0F) {
				this.hue = hsv[0];
			}

			this.saturation = hsv[1];
			this.value = hsv[2];
			this.synced = this.setting.get();
		}

		private void apply(int alpha) {
			this.setting.set(Colors.fromHsv(this.hue, this.saturation, this.value, alpha));
			this.synced = this.setting.get();
		}

		@Override
		float height() {
			return HEIGHT + this.open.value() * PICKER;
		}

		private float squareY() {
			return this.y + HEIGHT + GAP;
		}

		private float hueY() {
			return this.squareY() + SQUARE + GAP;
		}

		private float alphaY() {
			return this.hueY() + BAR + GAP;
		}

		private float fieldY() {
			return this.alphaY() + BAR + GAP;
		}

		private static float clamp(float fraction) {
			return Math.max(0.0F, Math.min(1.0F, fraction));
		}

		private void drag(float px, float py) {
			int alpha = this.setting.alpha();
			switch (this.dragging) {
				case SQUARE -> {
					this.saturation = clamp((px - this.x) / this.width);
					this.value = 1.0F - clamp((py - this.squareY()) / SQUARE);
					this.apply(alpha);
				}
				case HUE -> {
					this.hue = clamp((px - this.x) / this.width) * 0.9999F;
					this.apply(alpha);
				}
				case ALPHA -> this.apply(Math.round(clamp((px - this.x) / this.width) * 255.0F));
				case NONE -> {
				}
			}
		}

		@Override
		void render(Ui ui) {
			Theme theme = ui.theme();
			if (this.setting.get() != this.synced) {
				this.sync();
			}

			if (this.dragging != Part.NONE) {
				this.drag(ui.mouseX(), ui.mouseY());
			}

			Controls.State control = ui.controls().register(this.id, Controls.Kind.EXPANDER, this.x, this.y, this.width, HEIGHT,
				() -> this.opened ? "open" : "closed");
			this.open.animateTo(this.opened);
			float reveal = this.open.value();
			int colour = this.setting.get();
			ui.textInRow(Type.CAPTION, this.label(), this.x, this.y, HEIGHT, control.hovered() ? theme.text() : theme.textMuted());
			float sx = this.x + this.width - 16.0F;
			ui.rounded(sx, this.y + 3.0F, 16.0F, 9.0F, Radius.S, colour);
			ui.outline(sx, this.y + 3.0F, 16.0F, 9.0F, Radius.S, control.hovered() || control.pressed() ? Stroke.FOCUS : Stroke.HAIRLINE,
				control.hovered() || this.opened ? theme.text() : Colors.withAlpha(theme.text(), 60));
			if (reveal <= 0.01F) {
				return;
			}

			Ui picker = ui.faded(reveal);
			int pure = Colors.fromHsv(this.hue, 1.0F, 1.0F, 255);
			if (this.opened) {
				ui.controls().pushGroup(this.id);
				ui.controls().register(this.id + ":square", Controls.Kind.AREA, this.x, this.squareY(), this.width, SQUARE, this.setting::format);
				ui.controls().register(this.id + ":hue", Controls.Kind.SLIDER, this.x, this.hueY() - 1.0F, this.width, BAR + 2.0F, () -> Float.toString(this.hue));
				ui.controls().register(this.id + ":alpha", Controls.Kind.SLIDER, this.x, this.alphaY() - 1.0F, this.width, BAR + 2.0F, () -> Integer.toString(this.setting.alpha()));
				this.hex.setId(this.id + ":hex");
			}

			// Saturation runs left to right, value top to bottom: white-to-hue under a fade to black.
			float qy = this.squareY();
			picker.horizontalGradient(this.x, qy, this.width, SQUARE, Radius.S, Colors.WHITE, pure);
			picker.verticalGradient(this.x, qy, this.width, SQUARE, Radius.S, Colors.TRANSPARENT, Colors.BLACK);
			float cx = this.x + this.saturation * this.width;
			float cy = qy + (1.0F - this.value) * SQUARE;
			picker.outline(cx - 3.5F, cy - 3.5F, 7.0F, 7.0F, 3.5F, 1.0F, Colors.BLACK);
			picker.outline(cx - 2.5F, cy - 2.5F, 5.0F, 5.0F, 2.5F, 1.0F, Colors.WHITE);

			float hy = this.hueY();
			float segment = this.width / 6.0F;
			for (int i = 0; i < 6; i++) {
				picker.horizontalGradient(this.x + i * segment, hy, segment + 0.5F, BAR, 0.0F,
					Colors.fromHsv(i / 6.0F, 1.0F, 1.0F, 255), Colors.fromHsv((i + 1) / 6.0F, 1.0F, 1.0F, 255));
			}

			this.thumb(picker, this.x + this.hue * this.width, hy, theme);

			float ay = this.alphaY();
			picker.rounded(this.x, ay, this.width, BAR, Radius.S, theme.raised());
			picker.horizontalGradient(this.x, ay, this.width, BAR, Radius.S, Colors.withAlpha(colour, 0), Colors.withAlpha(colour, 255));
			this.thumb(picker, this.x + this.setting.alpha() / 255.0F * this.width, ay, theme);

			if (!this.hex.isFocused()) {
				this.hex.setText(this.setting.format());
			}

			if (this.opened) {
				this.hex.render(picker, this.x, this.fieldY(), this.width, FIELD, Radius.S, Type.MONO);
				ui.controls().popGroup();
			} else {
				this.hex.renderWithoutControl(picker, this.x, this.fieldY(), this.width, FIELD, Radius.S, Type.MONO);
			}
		}

		private void thumb(Ui ui, float centerX, float barY, Theme theme) {
			ui.rounded(centerX - 1.5F, barY - 1.5F, 3.0F, BAR + 3.0F, 1.5F, theme.text());
			ui.outline(centerX - 1.5F, barY - 1.5F, 3.0F, BAR + 3.0F, 1.5F, 0.5F, Colors.BLACK);
		}

		@Override
		boolean mouseDown(float px, float py, MouseButton button) {
			if (button != MouseButton.LEFT) {
				return false;
			}

			if (py < this.y + HEIGHT) {
				this.opened = !this.opened;
				this.hex.setFocused(false);
				return true;
			}

			if (!this.opened) {
				return false;
			}

			this.hex.setFocused(this.hex.contains(px, py));
			if (py >= this.squareY() && py < this.squareY() + SQUARE) {
				this.dragging = Part.SQUARE;
			} else if (py >= this.hueY() - 1.0F && py < this.hueY() + BAR + 1.0F) {
				this.dragging = Part.HUE;
			} else if (py >= this.alphaY() - 1.0F && py < this.alphaY() + BAR + 1.0F) {
				this.dragging = Part.ALPHA;
			}

			this.drag(px, py);
			return true;
		}

		@Override
		void mouseUp() {
			this.dragging = Part.NONE;
		}

		@Override
		boolean capturesKeys() {
			return this.hex.isFocused();
		}

		@Override
		boolean keyDown(Key key, boolean control, InputBackend input) {
			return this.hex.keyDown(key, control, input);
		}

		@Override
		void charTyped(String text) {
			this.hex.type(text);
		}

		@Override
		void blur() {
			this.dragging = Part.NONE;
			this.hex.setFocused(false);
		}
	}

	static final class KeyBinder extends SettingRow {
		private final KeySetting setting;
		private final Animation listen = new Animation(0.0F, Animation.FAST, Easing.OUT_CUBIC);
		private boolean listening;

		KeyBinder(KeySetting setting) {
			super(setting.name(), setting.description());
			this.setting = setting;
		}

		@Override
		float height() {
			return HEIGHT;
		}

		@Override
		void render(Ui ui) {
			Theme theme = ui.theme();
			Controls.State control = ui.controls().register(this.id, Controls.Kind.KEY, this.x, this.y, this.width, HEIGHT,
				() -> this.listening ? "listening" : "idle");
			this.listen.animateTo(this.listening);
			float lit = this.listen.value();
			ui.textInRow(Type.CAPTION, this.label(), this.x, this.y, HEIGHT, control.hovered() ? theme.text() : theme.textMuted());
			String shown = this.listening ? "press a key" : this.setting.format().replace('_', ' ');
			float boxWidth = Math.max(26.0F, Type.MONO.width(ui.renderer(), shown) + 8.0F);
			float bx = this.x + this.width - boxWidth;
			ui.rounded(bx, this.y + 2.0F, boxWidth, 11.0F, Radius.S, Ui.stateColor(theme.raised(), control));
			ui.outline(bx, this.y + 2.0F, boxWidth, 11.0F, Radius.S, Stroke.HAIRLINE,
				Colors.mix(control.hovered() ? theme.textMuted() : theme.outline(), theme.accent(), lit));
			boolean unbound = this.setting.get().isEmpty() && !this.listening;
			ui.textInRow(Type.MONO, shown, bx + (boxWidth - Type.MONO.width(ui.renderer(), shown)) / 2.0F, this.y + 2.0F, 11.0F,
				unbound ? theme.textDisabled() : Colors.mix(theme.text(), theme.accent(), lit));
		}

		@Override
		boolean mouseDown(float px, float py, MouseButton button) {
			if (button == MouseButton.RIGHT) {
				this.setting.set(Optional.empty());
				this.listening = false;
				return true;
			}

			if (button != MouseButton.LEFT) {
				return false;
			}

			this.listening = !this.listening;
			return true;
		}

		@Override
		boolean capturesKeys() {
			return this.listening;
		}

		// The next key pressed becomes the bind; Escape clears it instead.
		@Override
		boolean keyDown(Key key, boolean control, InputBackend input) {
			if (!this.listening) {
				return false;
			}

			this.setting.set(key == Key.ESCAPE ? Optional.empty() : Optional.of(key));
			this.listening = false;
			return true;
		}

		@Override
		void blur() {
			this.listening = false;
		}
	}

	static final class TextField extends SettingRow {
		private final StringSetting setting;
		private final TextInput input;

		TextField(StringSetting setting) {
			super(setting.name(), setting.description());
			this.setting = setting;
			this.input = new TextInput("", setting.defaultValue(), 64).onChange(setting::set);
		}

		@Override
		float height() {
			return 29.0F;
		}

		@Override
		void render(Ui ui) {
			this.input.setId(this.id);
			ui.textInRow(Type.CAPTION, this.label(), this.x, this.y, 13.0F, ui.theme().textMuted());
			if (!this.input.isFocused()) {
				this.input.setText(this.setting.get());
			}

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
}
