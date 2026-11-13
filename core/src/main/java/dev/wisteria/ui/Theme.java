package dev.wisteria.ui;

/**
 * Every colour the interface uses, by role. Widgets ask the theme for "surface" or "accent", never for a
 * hex value, so switching themes is one assignment.
 *
 * @param background the dimmed backdrop behind screens
 * @param surface panels
 * @param raised cards and controls on a panel
 * @param hover a raised control under the cursor
 * @param outline hairlines and control borders
 * @param text primary text
 * @param textMuted secondary text, labels
 * @param textDisabled text of unavailable controls
 * @param accent "on", selected, focused
 * @param accentHover accent under the cursor
 * @param accentPressed accent while held
 * @param onAccent text and icons drawn on the accent
 * @param gradientStart light end of the brand gradient
 * @param gradientEnd deep end of the brand gradient
 */
public record Theme(
	String id,
	String name,
	boolean dark,
	int background,
	int surface,
	int raised,
	int hover,
	int outline,
	int text,
	int textMuted,
	int textDisabled,
	int accent,
	int accentHover,
	int accentPressed,
	int onAccent,
	int gradientStart,
	int gradientEnd,
	int success,
	int warning,
	int danger,
	int shadow
) {
	/** The neutral half of a theme; the accent is chosen separately. */
	public enum Base {
		/** Near-black with a violet cast: the brand's own base. */
		DARK(true, 0xFF0E0B16, 0xFF17131F, 0xFF211B2E, 0xFF2A2339, 0xFF2E2740, 0xFFECE8F5, 0xFF9A92AD, 0xFF5F5871),
		/** Kept as a structure for a future light theme; not tuned yet. */
		LIGHT(false, 0xFFEDEAF4, 0xFFFAF9FD, 0xFFFFFFFF, 0xFFF1EEF8, 0xFFD9D4E6, 0xFF1B1626, 0xFF6B647D, 0xFFADA7BC);

		private final boolean dark;
		private final int background;
		private final int surface;
		private final int raised;
		private final int hover;
		private final int outline;
		private final int text;
		private final int textMuted;
		private final int textDisabled;

		Base(boolean dark, int background, int surface, int raised, int hover, int outline, int text, int textMuted, int textDisabled) {
			this.dark = dark;
			this.background = background;
			this.surface = surface;
			this.raised = raised;
			this.hover = hover;
			this.outline = outline;
			this.text = text;
			this.textMuted = textMuted;
			this.textDisabled = textDisabled;
		}
	}

	/** Builds a full theme from a neutral base and one accent colour; the accent's states are derived from it. */
	public static Theme of(String id, String name, Base base, int accent) {
		int opaque = Colors.withAlpha(accent, 255);
		return new Theme(
			id, name, base.dark,
			base.background, base.surface, base.raised, base.hover, base.outline,
			base.text, base.textMuted, base.textDisabled,
			opaque,
			Colors.lighten(opaque, 0.18F),
			Colors.darken(opaque, 0.18F),
			Colors.readableOn(opaque, 0xFF0E0B16, 0xFFFFFFFF),
			Colors.lighten(opaque, 0.55F),
			Colors.darken(Colors.fromHsv(Colors.toHsv(opaque)[0] - 0.03F, Math.min(1.0F, Colors.toHsv(opaque)[1] + 0.35F), Colors.toHsv(opaque)[2], 255), 0.2F),
			0xFF8CE0B5, 0xFFFFC97A, 0xFFFF7A93,
			base.dark ? 0xFF000000 : 0xFF3B3350
		);
	}
}
