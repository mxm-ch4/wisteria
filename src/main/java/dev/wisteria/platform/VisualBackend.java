package dev.wisteria.platform;

/** Purely visual tweaks of the game's own rendering. */
public interface VisualBackend {
	/**
	 * Overrides the brightness the lightmap is built with. The game's slider goes from 0 to 1;
	 * around 16 everything is fully lit. A value of zero or less removes the override.
	 */
	void setBrightness(float brightness);

	/**
	 * Where a point of the world was on screen in the last frame, in GUI pixels {x, y}, for labels drawn in
	 * {@code Render2D}; null when it was behind the camera or no frame was drawn yet.
	 */
	default float @org.jspecify.annotations.Nullable [] project(double x, double y, double z) {
		return null;
	}
}
