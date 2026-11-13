package dev.wisteria.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tokens and keys, read from {@code .minecraft/wisteria/.env} ({@code NAME=value} per line, {@code #} comments,
 * optional quotes). The file is the user's: it is never written, committed or shown. Values never appear in
 * logs or chat: {@link #redact} scrubs them out of any text the client prints. Only modules with
 * {@link Permission#SECRETS} get them.
 */
public final class Secrets {
	public static final String FILE = ".env";
	private static final String MASK = "****";
	/** Shorter values are not scrubbed from text: they would match ordinary words. */
	private static final int MIN_REDACT_LENGTH = 6;

	private final Path file;
	private volatile Map<String, String> values = Map.of();
	private volatile boolean loaded;

	/** @param wisteriaRoot {@code .minecraft/wisteria} */
	public Secrets(Path wisteriaRoot) {
		this.file = wisteriaRoot.resolve(FILE);
	}

	public Path file() {
		return this.file;
	}

	/**
	 * The secret called {@code name}, or empty if the file does not have it.
	 *
	 * @throws SecurityException when the module may not read secrets
	 */
	public Optional<Secret> get(String moduleId, String name) {
		Permission.SECRETS.check(moduleId);
		String value = this.values().get(name);
		return value == null || value.isEmpty() ? Optional.empty() : Optional.of(new Secret(name, value));
	}

	/** Reads the file again, after the user edited it. */
	public synchronized void reload() {
		Map<String, String> read = new LinkedHashMap<>();
		if (Files.isRegularFile(this.file)) {
			try {
				for (String line : Files.readAllLines(this.file, StandardCharsets.UTF_8)) {
					parse(line, read);
				}
			} catch (IOException e) {
				// No secrets rather than a crash; the message carries the path, not a value.
				dev.wisteria.Wisteria.LOG.warn("Could not read {}: {}", this.file, e.getClass().getSimpleName());
			}
		}

		this.values = java.util.Collections.unmodifiableMap(read);
		this.loaded = true;
	}

	private Map<String, String> values() {
		if (!this.loaded) {
			this.reload();
		}

		return this.values;
	}

	static void parse(String line, Map<String, String> into) {
		String trimmed = line.strip();
		if (trimmed.isEmpty() || trimmed.startsWith("#")) {
			return;
		}

		if (trimmed.startsWith("export ")) {
			trimmed = trimmed.substring("export ".length()).strip();
		}

		int equals = trimmed.indexOf('=');
		if (equals <= 0) {
			return;
		}

		String name = trimmed.substring(0, equals).strip();
		String value = trimmed.substring(equals + 1).strip();
		if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))) {
			value = value.substring(1, value.length() - 1);
		}

		into.put(name, value);
	}

	/** {@code text} with every secret value replaced by {@code ****}. */
	public String redact(String text) {
		String result = text;
		for (String value : this.values().values()) {
			if (value.length() >= MIN_REDACT_LENGTH && result.contains(value)) {
				result = result.replace(value, MASK);
			}
		}

		return result;
	}

	/** A copy of {@code error} safe to log: same type name and stack, messages redacted, causes included. */
	public Throwable redact(Throwable error) {
		RedactedException copy = new RedactedException(error.getClass().getName() + ": " + this.redact(String.valueOf(error.getMessage())));
		copy.setStackTrace(error.getStackTrace());
		if (error.getCause() != null && error.getCause() != error) {
			copy.initCause(this.redact(error.getCause()));
		}

		return copy;
	}

	/** Names only, for {@code .net} status lines. */
	public List<String> names() {
		return List.copyOf(this.values().keySet());
	}

	private static final class RedactedException extends Exception {
		RedactedException(String message) {
			super(message);
		}

		@Override
		public synchronized Throwable fillInStackTrace() {
			return this;
		}
	}
}
