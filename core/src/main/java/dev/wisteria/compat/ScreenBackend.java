package dev.wisteria.compat;

/** Full-screen client UI (ClickGUI, HUD editor) hosted in a game screen. */
public interface ScreenBackend {
	/**
	 * Opens a screen drawn by the client, replacing whatever screen is open. It appears on the next tick,
	 * so this is safe to call from a chat command while the chat box is still closing.
	 */
	void open(ClientScreen screen);

	/** Closes the client screen if one is open. */
	void close();

	/** True while any screen, the game's or ours, is open. */
	boolean isAnyOpen();

	interface ClientScreen {
		String title();

		/** Whether the game should blur the world behind the screen. */
		default boolean blurBackground() {
			return true;
		}

		void render(Renderer2D renderer, float mouseX, float mouseY, float tickDelta);

		default void onClose() {
		}
	}
}
