package dev.wisteria.platform;

public interface InputBackend {
	/** Whether the key is physically held right now. */
	boolean isDown(Key key);

	void listen(InputListener listener);

	/** The system clipboard as text; empty if it holds something else. */
	String clipboard();

	void setClipboard(String text);

	enum Action {
		PRESS, RELEASE, REPEAT
	}

	interface InputListener {
		/**
		 * A key changed state. Keys the client has no name for are not reported.
		 *
		 * @param inScreen true while a screen (chat, inventory, menu) has keyboard focus
		 */
		void onKey(Key key, Action action, boolean shift, boolean control, boolean alt, boolean inScreen);
	}
}
