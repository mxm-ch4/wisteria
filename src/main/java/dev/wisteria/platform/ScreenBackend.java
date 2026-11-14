package dev.wisteria.platform;

import dev.wisteria.render.Renderer2D;

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

	/** True while one of the client's own screens (ClickGUI, HUD editor) is open. */
	default boolean isClientOpen() {
		return false;
	}

	/** The game's own screens the main menu leads to. */
	enum GameScreen {
		SINGLEPLAYER, MULTIPLAYER, OPTIONS, CREDITS, QUIT
	}

	/** Opens one of the game's screens over the current one (Quit closes the game). */
	default void openGame(GameScreen screen) {
	}

	/**
	 * Gives the game's title screen the client's menu: whenever the game shows its title screen, the supplier is asked
	 * for a menu, and a null answer keeps the game's own.
	 */
	default void setMainMenu(java.util.function.Supplier<ClientScreen> menu) {
	}

	/** The game's text for a translation key, or the fallback where there is none (tests). */
	default String translate(String key, String fallback) {
		return fallback;
	}

	interface ClientScreen {
		String title();

		/** Whether the game should blur the world behind the screen. */
		default boolean blurBackground() {
			return true;
		}

		void render(Renderer2D renderer, float mouseX, float mouseY, float tickDelta);

		/** @return true if the click landed on something */
		default boolean mouseDown(float x, float y, MouseButton button) {
			return false;
		}

		default void mouseUp(float x, float y, MouseButton button) {
		}

		/** @param amount positive when scrolling up */
		default boolean mouseScroll(float x, float y, float amount) {
			return false;
		}

		/**
		 * @return true if the key was used; an unused Escape closes the screen
		 */
		default boolean keyDown(Key key, boolean shift, boolean control, boolean alt) {
			return false;
		}

		/** Text typed on the keyboard, already composed (layout, dead keys, IME). */
		default void charTyped(String text) {
		}

		default void onClose() {
		}
	}

	enum MouseButton {
		LEFT, RIGHT, MIDDLE
	}
}
