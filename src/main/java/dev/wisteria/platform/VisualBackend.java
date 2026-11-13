package dev.wisteria.platform;

/** Purely visual tweaks of the game's own rendering. */
public interface VisualBackend {
	/**
	 * Overrides the brightness the lightmap is built with. The game's slider goes from 0 to 1;
	 * around 16 everything is fully lit. A value of zero or less removes the override.
	 */
	void setBrightness(float brightness);
}
