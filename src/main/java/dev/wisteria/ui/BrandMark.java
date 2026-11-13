package dev.wisteria.ui;

import dev.wisteria.render.Renderer2D;

/** The wisteria mark (see tools/brand/generate.py): a hanging cluster of diamond blossoms. */
public final class BrandMark {
	// Row, column offset, scale. Rows hang half a step apart.
	static final float[][] CLUSTER = {
		{0, 0.0F, 0.80F},
		{1, -0.5F, 1.00F}, {1, 0.5F, 1.00F},
		{2, -1.0F, 0.86F}, {2, 0.0F, 1.00F}, {2, 1.0F, 0.86F},
		{3, -0.5F, 1.00F}, {3, 0.5F, 1.00F},
		{4, 0.0F, 1.00F},
		{5, -0.5F, 0.88F}, {5, 0.5F, 0.94F},
		{6, 0.0F, 0.86F},
		{7, 0.5F, 0.74F},
		{8, 0.0F, 0.62F},
		{9, 0.5F, 0.48F},
		{10, 0.0F, 0.34F},
	};

	private BrandMark() {
	}

	/** Width of the mark drawn with this step. */
	public static float width(float step) {
		return step * 2.6F;
	}

	public static float height(float step) {
		return step * 5.8F;
	}

	/**
	 * Draws the mark with its top-left corner at x, y.
	 *
	 * @param grow 0 to 1 per blossom index, for the splash's bloom; null for fully grown
	 */
	public static void draw(Renderer2D renderer, float x, float y, float step, Theme theme, float opacity, float[] grow) {
		float centerX = x + width(step) / 2.0F;
		float top = y + step * 0.45F;
		for (int i = 0; i < CLUSTER.length; i++) {
			float[] blossom = CLUSTER[i];
			float g = grow == null ? 1.0F : grow[i];
			float half = step * 0.43F * blossom[2] * g;
			if (half <= 0.05F) {
				continue;
			}

			float side = half * (float)Math.sqrt(2.0);
			int colour = Colors.mix(theme.gradientStart(), theme.gradientEnd(), blossom[0] / 10.0F);
			renderer.push();
			renderer.translate(centerX + blossom[1] * step, top + blossom[0] * step / 2.0F);
			renderer.rotate((float)(Math.PI / 4.0));
			renderer.roundedRect(-side / 2.0F, -side / 2.0F, side, side, side * 0.22F, Colors.fade(colour, opacity * (grow == null ? 1.0F : Math.min(1.0F, g * 2.0F))));
			renderer.pop();
		}
	}
}
