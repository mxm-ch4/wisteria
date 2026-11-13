package dev.wisteria.ui;

import dev.wisteria.render.FontFace;
import dev.wisteria.render.Renderer2D;

/** The fixed measures of the interface, in GUI pixels: spacing, corner radii, stroke widths, type sizes. */
public final class Metrics {
	private Metrics() {
	}

	/** Gaps and padding. Everything is laid out on these four steps. */
	public static final class Space {
		public static final float XS = 4.0F;
		public static final float S = 8.0F;
		public static final float M = 12.0F;
		public static final float L = 16.0F;

		private Space() {
		}
	}

	public static final class Radius {
		/** Small controls: checkboxes, swatches, slider tracks. */
		public static final float S = 3.0F;
		/** Buttons, cards, inputs. */
		public static final float M = 6.0F;
		/** Panels and windows. */
		public static final float L = 10.0F;

		private Radius() {
		}

		/** A fully rounded end for a bar of the given height. */
		public static float pill(float height) {
			return height / 2.0F;
		}
	}

	public static final class Stroke {
		public static final float HAIRLINE = 1.0F;
		public static final float FOCUS = 1.5F;

		private Stroke() {
		}
	}

	/** How text is rasterised. The one place to tune text quality for the whole interface. */
	public static final class Text {
		/**
		 * Glyphs are rasterised this many times larger than the screen pixels they cover, then sampled down with
		 * bilinear filtering. 2 gives smooth, still crisp edges; 1 is sharpest, 3-4 softer and heavier on memory.
		 */
		public static final int OVERSAMPLE = 2;
		/** Line box height in em; the font's ascent and descent are centred in it. */
		public static final float LINE_HEIGHT = 1.25F;

		private Text() {
		}
	}

	/** The type scale. Sizes are em heights; a line is {@link Text#LINE_HEIGHT} em tall. */
	public enum Type {
		/** Window and section titles. */
		TITLE(FontFace.SEMIBOLD, 13.0F),
		/** Module names, button labels. */
		LABEL(FontFace.SEMIBOLD, 10.0F),
		/** Setting names, descriptions. */
		BODY(FontFace.REGULAR, 10.0F),
		/** Hints and secondary lines. */
		CAPTION(FontFace.REGULAR, 8.0F),
		/** Numbers: values, coordinates, key names. */
		MONO(FontFace.MONO, 9.0F);

		private final FontFace face;
		private final float size;

		Type(FontFace face, float size) {
			this.face = face;
			this.size = size;
		}

		public FontFace face() {
			return this.face;
		}

		public float size() {
			return this.size;
		}

		public void draw(Renderer2D renderer, String text, float x, float y, int argb) {
			renderer.text(this.face, this.size, text, x, y, argb);
		}

		public float width(Renderer2D renderer, String text) {
			return renderer.textWidth(this.face, this.size, text);
		}

		public float lineHeight(Renderer2D renderer) {
			return renderer.lineHeight(this.face, this.size);
		}
	}
}
