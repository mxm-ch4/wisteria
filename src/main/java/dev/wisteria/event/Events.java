package dev.wisteria.event;

import dev.wisteria.platform.InputBackend;
import dev.wisteria.platform.Key;
import dev.wisteria.platform.PacketAdapter;
import dev.wisteria.render.Renderer2D;
import dev.wisteria.render.Renderer3D;

/** The events the client core publishes on its {@link EventBus}. */
public final class Events {
	private Events() {
	}

	/** Once per client tick, on the client thread. */
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

	/** A packet is about to leave. Network or client thread. */
	public static final class PacketSend extends Cancellable {
		private final PacketAdapter.PacketView packet;

		public PacketSend(PacketAdapter.PacketView packet) {
			this.packet = packet;
		}

		public PacketAdapter.PacketView packet() {
			return this.packet;
		}
	}

	/** A packet arrived and is about to be handled. Network thread. */
	public static final class PacketReceive extends Cancellable {
		private final PacketAdapter.PacketView packet;

		public PacketReceive(PacketAdapter.PacketView packet) {
			this.packet = packet;
		}

		public PacketAdapter.PacketView packet() {
			return this.packet;
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
