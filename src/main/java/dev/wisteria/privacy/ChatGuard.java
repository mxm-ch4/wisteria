package dev.wisteria.privacy;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Decides whether an outgoing chat line or command would give something away, as a pure function: a saved secret,
 * the proxy's address or login, or one of the guard words (the client's name, its module names). Lines that start
 * with the command prefix never get here: the client keeps those anyway.
 */
public final class ChatGuard {
	/** Secrets shorter than this are not matched: they would hit ordinary words. */
	static final int MIN_SECRET = 6;

	private ChatGuard() {
	}

	/** @return why the line must not be sent, or null if it may */
	public static @Nullable String check(String line, List<String> secrets, List<String> proxyBits, List<String> words) {
		String lower = line.toLowerCase(Locale.ROOT);
		for (String secret : secrets) {
			if (secret.length() >= MIN_SECRET && line.contains(secret)) {
				return "it contains one of your saved secrets";
			}
		}

		for (String bit : proxyBits) {
			if (!bit.isBlank() && lower.contains(bit.toLowerCase(Locale.ROOT))) {
				return "it contains your proxy's address or login";
			}
		}

		for (String word : words) {
			String trimmed = word.strip();
			if (trimmed.isEmpty()) {
				continue;
			}

			// Whole words only: "esp" in "especially" is fine.
			Pattern pattern = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(trimmed.toLowerCase(Locale.ROOT)) + "(?![\\p{L}\\p{N}])");
			if (pattern.matcher(lower).find()) {
				return "it mentions \"" + trimmed + "\"";
			}
		}

		return null;
	}
}
