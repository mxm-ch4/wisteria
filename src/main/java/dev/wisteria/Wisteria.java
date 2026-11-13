package dev.wisteria;

import dev.wisteria.platform.Platform;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Wisteria {
	public static final String ID = "wisteria";
	public static final String NAME = "Wisteria";
	public static final Logger LOG = LoggerFactory.getLogger(NAME);

	private static @Nullable Client client;

	private Wisteria() {
	}

	/** Called once by the version-specific entrypoint when the game starts. */
	public static void start(Platform platform) {
		if (client != null) {
			throw new IllegalStateException("Wisteria has already started");
		}

		client = new Client(platform);
		LOG.info("{} loaded on Minecraft {}: {} modules, profile \"{}\"",
			NAME, platform.minecraftVersion(), client.modules().all().size(), client.config().profile());
	}

	/** The running client. Only valid after {@link #start}. */
	public static Client client() {
		Client current = client;
		if (current == null) {
			throw new IllegalStateException("Wisteria has not started yet");
		}

		return current;
	}

	/**
	 * The running client, or null before it has started (or when it is switched off): for hooks in the game, which can
	 * run before the client exists.
	 */
	public static @org.jspecify.annotations.Nullable Client clientOrNull() {
		return client;
	}

	/**
	 * Posts an event on the client's bus; does nothing before the client has started. For hooks in the game
	 * (mixins, platform code) that fire events.
	 *
	 * @return the event, so a caller can read back whether it was cancelled
	 */
	public static <E> E post(E event) {
		Client current = client;
		return current != null ? current.bus().post(event) : event;
	}

	/** The game we are running in. Only valid after {@link #start}. */
	public static Platform platform() {
		return client().platform();
	}
}
