package dev.wisteria.compat.common;

import dev.wisteria.compat.VisualBackend;

public final class CommonVisuals implements VisualBackend {
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
