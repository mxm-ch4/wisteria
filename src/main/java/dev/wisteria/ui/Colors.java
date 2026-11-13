package dev.wisteria.ui;

/** ARGB colour arithmetic. Colours are plain ints, 0xAARRGGBB, as everywhere else in the client. */
public final class Colors {
	public static final int WHITE = 0xFFFFFFFF;
	public static final int BLACK = 0xFF000000;
	public static final int TRANSPARENT = 0x00000000;

	private Colors() {
	}

	public static int argb(int alpha, int red, int green, int blue) {
		return clamp(alpha) << 24 | clamp(red) << 16 | clamp(green) << 8 | clamp(blue);
	}

	public static int alpha(int argb) {
		return argb >>> 24;
	}

	public static int red(int argb) {
		return argb >> 16 & 0xFF;
	}

	public static int green(int argb) {
		return argb >> 8 & 0xFF;
	}

	public static int blue(int argb) {
		return argb & 0xFF;
	}

	/** The colour with its alpha replaced by 0..255. */
	public static int withAlpha(int argb, int alpha) {
		return (argb & 0x00FFFFFF) | clamp(alpha) << 24;
	}

	/** The colour with its alpha multiplied by 0..1: for fading something in or out. */
	public static int fade(int argb, float opacity) {
		return withAlpha(argb, Math.round(alpha(argb) * Math.max(0.0F, Math.min(1.0F, opacity))));
	}

	/** Linear blend of every channel: 0 gives {@code from}, 1 gives {@code to}. */
	public static int mix(int from, int to, float amount) {
		float t = Math.max(0.0F, Math.min(1.0F, amount));
		return argb(
			Math.round(alpha(from) + (alpha(to) - alpha(from)) * t),
			Math.round(red(from) + (red(to) - red(from)) * t),
			Math.round(green(from) + (green(to) - green(from)) * t),
			Math.round(blue(from) + (blue(to) - blue(from)) * t)
		);
	}

	public static int lighten(int argb, float amount) {
		return withAlpha(mix(argb, WHITE, amount), alpha(argb));
	}

	public static int darken(int argb, float amount) {
		return withAlpha(mix(argb, BLACK, amount), alpha(argb));
	}

	/** Perceived brightness, 0 (black) to 1 (white). */
	public static float luminance(int argb) {
		return (0.2126F * red(argb) + 0.7152F * green(argb) + 0.0722F * blue(argb)) / 255.0F;
	}

	/** Dark text on light colours, light text on dark ones. */
	public static int readableOn(int background, int dark, int light) {
		return luminance(background) > 0.55F ? dark : light;
	}

	/**
	 * @param hue 0..1 around the colour wheel
	 * @param saturation 0..1
	 * @param value 0..1
	 */
	public static int fromHsv(float hue, float saturation, float value, int alpha) {
		float h = (hue - (float)Math.floor(hue)) * 6.0F;
		float s = Math.max(0.0F, Math.min(1.0F, saturation));
		float v = Math.max(0.0F, Math.min(1.0F, value));
		int sector = (int)Math.floor(h) % 6;
		float f = h - (float)Math.floor(h);
		float p = v * (1.0F - s);
		float q = v * (1.0F - s * f);
		float t = v * (1.0F - s * (1.0F - f));
		float r;
		float g;
		float b;
		switch (sector) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}

		return argb(alpha, Math.round(r * 255.0F), Math.round(g * 255.0F), Math.round(b * 255.0F));
	}

	/** @return {hue, saturation, value}, each 0..1 */
	public static float[] toHsv(int argb) {
		float r = red(argb) / 255.0F;
		float g = green(argb) / 255.0F;
		float b = blue(argb) / 255.0F;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float delta = max - min;
		float hue;
		if (delta == 0.0F) {
			hue = 0.0F;
		} else if (max == r) {
			hue = ((g - b) / delta + 6.0F) % 6.0F;
		} else if (max == g) {
			hue = (b - r) / delta + 2.0F;
		} else {
			hue = (r - g) / delta + 4.0F;
		}

		return new float[] {hue / 6.0F, max == 0.0F ? 0.0F : delta / max, max};
	}

	private static int clamp(int channel) {
		return Math.max(0, Math.min(255, channel));
	}
}
