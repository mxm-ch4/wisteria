package dev.wisteria.compat;

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

	/** The .minecraft directory of this instance. */
	Path gameDirectory();

	/** Frame and tick callbacks. Listeners are called in registration order, on the client thread. */
	void listen(ClientListener listener);

	InputBackend input();

	AudioBackend audio();

	PacketAdapter packets();

	ScreenBackend screens();
}
