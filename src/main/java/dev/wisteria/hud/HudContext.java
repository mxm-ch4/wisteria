package dev.wisteria.hud;

import dev.wisteria.render.Renderer2D;
import dev.wisteria.ui.Colors;
import dev.wisteria.ui.Metrics;
import dev.wisteria.ui.Metrics.Type;
import dev.wisteria.ui.Theme;

/**
 * What a HUD element draws with: the renderer, the current theme, and how far it has faded in. Every colour
 * goes through {@link #color} so an element appears and disappears smoothly without doing anything itself.
 */
public final class HudContext {
	/** Inner padding of a HUD panel. */
	public static final float PAD = 5.0F;

	private final Renderer2D renderer;
	private final Theme theme;
	private final float alpha;
	private final boolean preview;
	private final Anchor anchor;

	public HudContext(Renderer2D renderer, Theme theme, float alpha, boolean preview, Anchor anchor) {
		this.renderer = renderer;
		this.theme = theme;
		this.alpha = alpha;
		this.preview = preview;
		this.anchor = anchor;
	}

	public Renderer2D renderer() {
		return this.renderer;
	}

	public Theme theme() {
		return this.theme;
	}

	/** 0 to 1 while the element fades in or out. */
	public float alpha() {
		return this.alpha;
	}

	/** True in the HUD editor: show sample content when there is nothing real, so it can be placed. */
	public boolean preview() {
		return this.preview;
	}

	/** Where the element is pinned: elements on the right edge align their lines to the right. */
	public Anchor anchor() {
		return this.anchor;
	}

	public int color(int argb) {
		return Colors.fade(argb, this.alpha);
	}

	/** The standard HUD panel: raised surface, rounded, with a hairline outline. */
	public void panel(float x, float y, float width, float height) {
		this.renderer.roundedRect(x, y, width, height, Metrics.Radius.M, this.color(Colors.withAlpha(this.theme.surface(), 0xE0)));
		this.renderer.roundedOutline(x, y, width, height, Metrics.Radius.M, Metrics.Stroke.HAIRLINE, this.color(Colors.withAlpha(this.theme.outline(), 0x90)));
	}

	public void text(Type type, String text, float x, float y, int argb) {
		type.draw(this.renderer, text, x, y, this.color(argb));
	}

	public float width(Type type, String text) {
		return type.width(this.renderer, text);
	}

	public float line(Type type) {
		return type.lineHeight(this.renderer);
	}

	/** A thin progress bar: track plus accent fill. */
	public void bar(float x, float y, float width, float height, float progress, int argb) {
		float radius = height / 2.0F;
		this.renderer.roundedRect(x, y, width, height, radius, this.color(Colors.withAlpha(this.theme.outline(), 0xA0)));
		float filled = Math.max(height, width * Math.max(0.0F, Math.min(1.0F, progress)));
		this.renderer.roundedRect(x, y, filled, height, radius, this.color(argb));
	}
}
