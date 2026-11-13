package dev.wisteria.platform;

import dev.wisteria.render.Renderer2D;
import dev.wisteria.render.Renderer3D;

/** Per-tick and per-frame callbacks from the game. */
public interface ClientListener {
	/** Once per client tick, before the game's own tick. */
	default void onTickStart() {
	}

	/** The player is in a world and can act. */
	default void onJoin() {
	}

	/** The player has left the world. */
	default void onLeave() {
	}

	/** Once per client tick (20 per second), after the game's own tick. */
	default void onTick() {
	}

	/** Once per frame while the HUD is visible. Drawing is only valid inside this call. */
	default void onRender2D(Renderer2D renderer, float tickDelta) {
	}

	/** Once per frame while a world is loaded. Drawing is only valid inside this call. */
	default void onRender3D(Renderer3D renderer, float tickDelta) {
	}

	/** The title screen is showing for the first time since launch: the game has finished loading. */
	default void onTitleScreen() {
	}

	/** The game is shutting down: last chance to save. */
	default void onStop() {
	}
}
