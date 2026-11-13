package dev.wisteria.compat;

/** Full-screen client UI (ClickGUI, HUD editor) hosted in a game screen. */
public interface ScreenBackend {
	/** Opens a screen drawn by the client; replaces whatever screen is open. */
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
