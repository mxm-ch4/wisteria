package dev.wisteria.platform;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** The chat box: where client commands are typed and where the client talks back. */
public interface ChatBackend {
	/** Shows a client-side line in chat, prefixed with the client's name. Nothing is sent to the server. */
	void print(String message);

	/** Called for every chat message the player is about to send. */
	void listen(ChatListener listener);

	/** Supplies tab completions for whatever is typed in the chat box. Only one provider is kept. */
	void completions(CompletionProvider provider);

	interface ChatListener {
		/** @return true to swallow the message instead of sending it to the server */
		boolean onSend(String message);
	}

	interface CompletionProvider {
		/**
		 * @param input the text before the cursor
		 * @return completions for the word under the cursor, or null to leave it to the game
		 */
		@Nullable Completion complete(String input);
	}

	/**
	 * @param start index in the input where the completed word begins
	 * @param candidates full replacements for that word
	 */
	record Completion(int start, List<String> candidates) {
	}
}
