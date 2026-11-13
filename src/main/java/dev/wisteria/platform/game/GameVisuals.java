package dev.wisteria.platform.game;

import dev.wisteria.platform.VisualBackend;

public final class GameVisuals implements VisualBackend {
	private volatile float brightness;

	@Override
	public void setBrightness(float brightness) {
		this.brightness = Math.max(0.0F, brightness);
	}

	/** Read by the lightmap mixin every time the game rebuilds the lightmap; zero means no override. */
	public float brightness() {
		return this.brightness;
	}
}
