package dev.wisteria.platform;

import java.nio.file.Path;

/**
 * Everything the client needs from the game. One implementation per supported Minecraft version;
 * the rest of the client only ever sees these interfaces.
 */
public interface Platform {
	/** The Minecraft version this implementation was built for, e.g. "26.2". */
	String minecraftVersion();

	/** "OpenGL" or "Vulkan". */
	String graphicsBackend();

	/**
	 * Writes the record of everything sent to servers this session to .minecraft/wisteria/audit/ and opens the folder.
	 * Does nothing where there is no game (tests).
	 */
	default void writeNetworkAudit() {
	}

	/** The client's own version, e.g. "0.1.0". */
	default String clientVersion() {
		return "dev";
	}

	/** The .minecraft directory of this instance. */
	Path gameDirectory();

	/** Runs the task on the client thread: right away if called from it, otherwise on its next iteration. */
	void execute(Runnable task);

	/** Frame and tick callbacks. Listeners are called in registration order, on the client thread. */
	void listen(ClientListener listener);

	InputBackend input();

	AudioBackend audio();

	PacketAdapter packets();

	ScreenBackend screens();

	ChatBackend chat();

	WorldBackend world();

	VisualBackend visuals();
}
