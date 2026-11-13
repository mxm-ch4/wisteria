package dev.wisteria.ui.gui;

import dev.wisteria.platform.InputBackend;
import dev.wisteria.platform.Key;
import dev.wisteria.ui.Animation;
import dev.wisteria.ui.Colors;
import dev.wisteria.ui.Easing;
import dev.wisteria.ui.Metrics.Stroke;
import dev.wisteria.ui.Metrics.Type;
import dev.wisteria.ui.Theme;
import java.util.function.Consumer;

/** A one-line text field. The caret sits at the end: enough for search, names and hex colours. */
public final class TextInput {
	private final int maxLength;
	private final String placeholder;
	private final Animation focus = new Animation(0.0F, Animation.FAST, Easing.OUT_CUBIC);
	private Consumer<String> onChange = text -> {
	};
	private Consumer<String> onCommit = text -> {
	};
	private String text = "";
	private boolean focused;
	private float x;
	private float y;
	private float width;
	private float height;

	public TextInput(String placeholder, int maxLength) {
		this.placeholder = placeholder;
		this.maxLength = maxLength;
	}

	/** Called on every edit. */
	public TextInput onChange(Consumer<String> listener) {
		this.onChange = listener;
		return this;
	}

	/** Called when editing ends: Enter, or focus moving elsewhere. */
	public TextInput onCommit(Consumer<String> listener) {
		this.onCommit = listener;
		return this;
	}

	public String text() {
		return this.text;
	}

	/** Replaces the text without notifying listeners. */
	public void setText(String text) {
		this.text = text.length() > this.maxLength ? text.substring(0, this.maxLength) : text;
	}

	public boolean isFocused() {
		return this.focused;
	}

	public void setFocused(boolean focused) {
		if (this.focused && !focused) {
			this.onCommit.accept(this.text);
		}

		this.focused = focused;
	}

	public boolean contains(float px, float py) {
		return Ui.inside(px, py, this.x, this.y, this.width, this.height);
	}

	public void render(Ui ui, float x, float y, float width, float height, float radius, Type style) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		Theme theme = ui.theme();
		this.focus.animateTo(this.focused);
		float lit = this.focus.value();
		ui.rounded(x, y, width, height, radius, theme.raised());
		ui.outline(x, y, width, height, radius, Stroke.HAIRLINE, Colors.mix(theme.outline(), theme.accent(), lit));

		float padding = Math.max(4.0F, radius * 0.8F);
		float room = width - 2.0F * padding;
		if (this.text.isEmpty() && !this.focused) {
			ui.textInRow(style, ui.fit(style, this.placeholder, room), x + padding, y, height, theme.textDisabled());
			return;
		}

		// Long text scrolls left so the end, where the caret is, stays visible.
		String shown = this.text;
		while (shown.length() > 1 && style.width(ui.renderer(), shown) > room - 2.0F) {
			shown = shown.substring(1);
		}

		ui.textInRow(style, shown, x + padding, y, height, theme.text());
		if (this.focused && (System.nanoTime() / 500_000_000L) % 2L == 0L) {
			float caretX = x + padding + style.width(ui.renderer(), shown) + 1.0F;
			ui.rect(caretX, y + (height - style.size()) / 2.0F, 1.0F, style.size(), theme.accent());
		}
	}

	/** @return true if the key was used */
	public boolean keyDown(Key key, boolean control, InputBackend input) {
		if (!this.focused) {
			return false;
		}

		switch (key) {
			case BACKSPACE -> {
				if (!this.text.isEmpty()) {
					int cut = control ? 0 : this.text.offsetByCodePoints(this.text.length(), -1);
					this.edit(this.text.substring(0, cut));
				}
			}
			case ENTER, NUMPAD_ENTER, ESCAPE -> this.setFocused(false);
			case V -> {
				if (control) {
					this.type(input.clipboard());
				}
			}
			case C -> {
				if (control) {
					input.setClipboard(this.text);
				}
			}
			default -> {
				// Letters and digits arrive again as typed text; swallowing the key keeps binds from firing.
			}
		}

		return true;
	}

	public void type(String typed) {
		if (!this.focused) {
			return;
		}

		StringBuilder clean = new StringBuilder(this.text);
		typed.codePoints().filter(point -> point >= 32 && point != 127).forEach(clean::appendCodePoint);
		String result = clean.length() > this.maxLength ? clean.substring(0, this.maxLength) : clean.toString();
		if (!result.equals(this.text)) {
			this.edit(result);
		}
	}

	private void edit(String text) {
		this.text = text;
		this.onChange.accept(text);
	}
}
