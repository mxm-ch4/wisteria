package dev.wisteria.event;

import dev.wisteria.platform.InputBackend;
import dev.wisteria.platform.Key;
import dev.wisteria.platform.PacketAdapter;
import net.minecraft.network.protocol.Packet;
import dev.wisteria.render.Renderer2D;
import dev.wisteria.render.Renderer3D;

/** The events the client core publishes on its {@link EventBus}. */
public final class Events {
	private Events() {
	}

	/** Once per client tick, after the game's own tick, on the client thread. */
	public record Tick() {
		public static final Tick INSTANCE = new Tick();
	}

	/** Once per frame while the HUD is visible. Draw only while handling this. */
	public record Render2D(Renderer2D renderer, float tickDelta) {
	}

	/** Once per frame while in a world. Draw only while handling this. */
	public record Render3D(Renderer3D renderer, float tickDelta) {
	}

	/** A key changed state. */
	public record KeyInput(Key key, InputBackend.Action action, boolean shift, boolean control, boolean alt, boolean inScreen) {
	}

	/** Once per client tick, before the game's own tick: the place to change input or state the tick will use. */
	public record TickStart() {
		public static final TickStart INSTANCE = new TickStart();
	}

	/** The player has joined a world or server and can act. */
	public record Join() {
		public static final Join INSTANCE = new Join();
	}

	/** The player has left the world or server: forget per-world state here. */
	public record Leave() {
		public static final Leave INSTANCE = new Leave();
	}

	/** A packet is about to leave. Network or client thread: touch the game only through Platform.execute. */
	public static final class PacketSend extends Cancellable {
		private final PacketAdapter.PacketView view;

		public PacketSend(PacketAdapter.PacketView view) {
			this.view = view;
		}

		/** The game's packet; test with instanceof. */
		public Packet<?> packet() {
			return this.view.packet();
		}

		/** The packet's class name, e.g. "ServerboundSwingPacket"; handy in logs. */
		public String name() {
			return this.view.name();
		}
	}

	/** A packet arrived and is about to be handled. Network thread: touch the game only through Platform.execute. */
	public static final class PacketReceive extends Cancellable {
		private final PacketAdapter.PacketView view;

		public PacketReceive(PacketAdapter.PacketView view) {
			this.view = view;
		}

		public Packet<?> packet() {
			return this.view.packet();
		}

		public String name() {
			return this.view.name();
		}
	}

	public abstract static class Cancellable {
		private volatile boolean cancelled;

		public void cancel() {
			this.cancelled = true;
		}

		public boolean isCancelled() {
			return this.cancelled;
		}
	}
}
