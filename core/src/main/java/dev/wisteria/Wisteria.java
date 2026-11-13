package dev.wisteria;

import dev.wisteria.compat.Platform;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Wisteria {
	public static final String ID = "wisteria";
	public static final String NAME = "Wisteria";
	public static final Logger LOG = LoggerFactory.getLogger(NAME);

	private static @Nullable Platform platform;

	private Wisteria() {
	}

	/** Called once by the version-specific entrypoint when the game starts. */
	public static void start(Platform platform) {
		Wisteria.platform = platform;
		LOG.info("{} loaded on Minecraft {}", NAME, platform.minecraftVersion());
	}

	/** The game we are running in. Only valid after {@link #start}. */
	public static Platform platform() {
		Platform current = platform;
		if (current == null) {
			throw new IllegalStateException("Wisteria has not started yet");
		}

		return current;
	}
}
