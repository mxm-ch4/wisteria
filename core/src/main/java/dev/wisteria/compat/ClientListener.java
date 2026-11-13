package dev.wisteria.compat;

/** Per-tick and per-frame callbacks from the game. */
public interface ClientListener {
	/** Once per client tick (20 per second), after the game's own tick. */
	default void onTick() {
	}

	/** Once per frame while the HUD is visible. Drawing is only valid inside this call. */
	default void onRender2D(Renderer2D renderer, float tickDelta) {
	}

	/** Once per frame while a world is loaded. Drawing is only valid inside this call. */
	default void onRender3D(Renderer3D renderer, float tickDelta) {
	}

	/** The game is shutting down: last chance to save. */
	default void onStop() {
	}
}
