package dev.wisteria.ui.gui;

import dev.wisteria.render.Renderer2D;
import dev.wisteria.ui.Colors;
import dev.wisteria.ui.Metrics.Type;
import dev.wisteria.ui.Theme;

/**
 * What a widget needs to draw one frame: the renderer, the active theme, where the cursor is, and an overall
 * opacity (the screen's fade-in) that every colour is multiplied by.
 */
public record Ui(Renderer2D renderer, Theme theme, float mouseX, float mouseY, float opacity, Controls controls) {
	/** The same context with everything drawn fainter: for content that is fading in or out. */
	public Ui faded(float factor) {
		return new Ui(this.renderer, this.theme, this.mouseX, this.mouseY, this.opacity * factor, this.controls);
	}

	/** A colour adjusted for a control's state: lighter under the cursor, darker while pressed, faint when disabled. */
	public static int stateColor(int base, Controls.State state) {
		if (!state.enabled()) {
			return Colors.fade(base, 0.45F);
		}

		if (state.pressed()) {
			return Colors.darken(base, 0.14F);
		}

		return state.hovered() ? Colors.lighten(base, 0.1F) : base;
	}

	public boolean hovering(float x, float y, float width, float height) {
		return inside(this.mouseX, this.mouseY, x, y, width, height);
	}

	public static boolean inside(float px, float py, float x, float y, float width, float height) {
		return px >= x && px < x + width && py >= y && py < y + height;
	}

	public int fade(int argb) {
		return Colors.fade(argb, this.opacity);
	}

	public void rect(float x, float y, float width, float height, int argb) {
		this.renderer.rect(x, y, width, height, this.fade(argb));
	}

	public void rounded(float x, float y, float width, float height, float radius, int argb) {
		this.renderer.roundedRect(x, y, width, height, radius, this.fade(argb));
	}

	public void outline(float x, float y, float width, float height, float radius, float thickness, int argb) {
		this.renderer.roundedOutline(x, y, width, height, radius, thickness, this.fade(argb));
	}

	public void shadow(float x, float y, float width, float height, float radius, float blur, int argb) {
		this.renderer.shadow(x, y, width, height, radius, blur, this.fade(argb));
	}

	public void horizontalGradient(float x, float y, float width, float height, float radius, int left, int right) {
		int from = this.fade(left);
		int to = this.fade(right);
		this.renderer.roundedGradient(x, y, width, height, radius, from, to, from, to);
	}

	public void verticalGradient(float x, float y, float width, float height, float radius, int top, int bottom) {
		int from = this.fade(top);
		int to = this.fade(bottom);
		this.renderer.roundedGradient(x, y, width, height, radius, from, from, to, to);
	}

	public void text(Type style, String text, float x, float y, int argb) {
		// The game draws fully transparent text as opaque, so nearly invisible text is skipped instead.
		int colour = this.fade(argb);
		if ((colour >>> 24) > 8 && !text.isEmpty()) {
			style.draw(this.renderer, text, x, y, colour);
		}
	}

	/** Text vertically centred in a row that starts at {@code y} and is {@code height} tall. */
	public void textInRow(Type style, String text, float x, float y, float height, int argb) {
		this.text(style, text, x, y + (height - style.size() * 1.25F) / 2.0F, argb);
	}

	public void textRightInRow(Type style, String text, float right, float y, float height, int argb) {
		this.textInRow(style, text, right - style.width(this.renderer, text), y, height, argb);
	}

	/** Cuts the text down to a width, ending in an ellipsis when something had to go. */
	public String fit(Type style, String text, float maxWidth) {
		if (style.width(this.renderer, text) <= maxWidth) {
			return text;
		}

		String cut = text;
		while (cut.length() > 1 && style.width(this.renderer, cut + "…") > maxWidth) {
			cut = cut.substring(0, cut.length() - 1);
		}

		return cut + "…";
	}

	/** A small arrowhead built from two strokes; angle 0 points right, a quarter turn points down. */
	public void chevron(float centerX, float centerY, float size, float radians, int argb) {
		Renderer2D r = this.renderer;
		int colour = this.fade(argb);
		r.push();
		r.translate(centerX, centerY);
		r.rotate(radians);
		for (int side = -1; side <= 1; side += 2) {
			r.push();
			r.translate(size * 0.25F, 0.0F);
			r.rotate(side * (float)(Math.PI * 0.25));
			r.roundedRect(-size * 0.75F, -0.6F, size * 0.75F, 1.2F, 0.6F, colour);
			r.pop();
		}

		r.pop();
	}
}
