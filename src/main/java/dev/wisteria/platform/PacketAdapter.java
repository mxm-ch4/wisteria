package dev.wisteria.platform;

import net.minecraft.network.protocol.Packet;

/** The network connection to the server the player is on. */
public interface PacketAdapter {
	void listen(PacketListener listener);

	/** A packet as the client core sees it: a name, and the game's own object kept opaque. */
	interface PacketView {
		/** The game's packet object: cast to the class you expect, e.g. ServerboundMovePlayerPacket. */
		Packet<?> packet();

		/** The packet's class name in the game, e.g. "ServerboundSwingPacket". Names can change between versions. */
		String name();
	}

	interface PacketListener {
		/** @return true to drop the packet instead of sending it */
		default boolean onSend(PacketView packet) {
			return false;
		}

		/** @return true to drop the packet instead of handling it */
		default boolean onReceive(PacketView packet) {
			return false;
		}
	}
}
