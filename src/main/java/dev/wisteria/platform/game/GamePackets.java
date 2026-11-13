package dev.wisteria.platform.game;

import dev.wisteria.platform.PacketAdapter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.network.protocol.Packet;

public final class GamePackets implements PacketAdapter {
	private final List<PacketListener> listeners = new CopyOnWriteArrayList<>();

	@Override
	public void listen(PacketListener listener) {
		this.listeners.add(listener);
	}

	/** Called by the connection mixin, possibly from the network thread. @return true to cancel */
	public boolean dispatchSend(Packet<?> packet) {
		if (this.listeners.isEmpty()) {
			return false;
		}

		View view = new View(packet);
		boolean cancel = false;
		for (PacketListener listener : this.listeners) {
			cancel |= listener.onSend(view);
		}

		return cancel;
	}

	/** Called by the connection mixin on the network thread. @return true to cancel */
	public boolean dispatchReceive(Packet<?> packet) {
		if (this.listeners.isEmpty()) {
			return false;
		}

		View view = new View(packet);
		boolean cancel = false;
		for (PacketListener listener : this.listeners) {
			cancel |= listener.onReceive(view);
		}

		return cancel;
	}

	private record View(Packet<?> packet) implements PacketView {
		@Override
		public String name() {
			return this.packet.getClass().getSimpleName();
		}
	}
}
