package dev.wisteria.event;

import dev.wisteria.platform.InputBackend;
import dev.wisteria.platform.Key;
import dev.wisteria.platform.PacketAdapter;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
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

	// ---- World (all on the client thread) ----

	/** A chunk arrived from the server and is now in the client's world. Its blocks can be read. */
	public record ChunkLoad(LevelChunk chunk) {
	}

	/** A chunk is about to leave the client's world: forget what you kept about it, or persist it now. */
	public record ChunkUnload(LevelChunk chunk) {
	}

	/**
	 * A block changed in the loaded world: from the server or from the player's own prediction.
	 * Not fired for whole chunks arriving; use ChunkLoad for those.
	 */
	public record BlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
	}

	/** An entity joined the client's world (came into range or spawned). */
	public record EntityAdd(Entity entity) {
	}

	/** An entity left the client's world (out of range, died, despawned). */
	public record EntityRemove(Entity entity) {
	}

	// ---- Containers (client thread) ----

	/**
	 * The server opened a container screen.
	 *
	 * @param menu the menu type, e.g. "minecraft:generic_9x3", "minecraft:shulker_box"
	 * @param pos the block the player last used, if that happened just before; null for entities and remote opens
	 */
	public record ContainerOpen(int containerId, String menu, Component title, @Nullable BlockPos pos) {
	}

	/**
	 * The server sent the full contents of a container. Container 0 is the player's own inventory.
	 *
	 * @param items every slot, container first, then the player's inventory
	 * @param containerSlots how many of {@code items} belong to the container itself
	 * @param pos the block it belongs to, as in ContainerOpen; null when unknown
	 */
	public record ContainerContent(int containerId, List<ItemStack> items, int containerSlots, @Nullable BlockPos pos) {
		/** Just the container's own slots, without the player's inventory. */
		public List<ItemStack> containerItems() {
			return this.items.subList(0, Math.min(this.containerSlots, this.items.size()));
		}
	}

	// ---- Chat (client thread) ----

	/**
	 * A chat line arrived. Cancel it to hide it from the chat.
	 *
	 * @param system true for server and game messages, false for a player's message
	 * @param sender the player's name for player messages; null for system ones
	 * @param overlay true for action-bar messages above the hotbar
	 */
	public static final class ChatReceive extends Cancellable {
		private final Component message;
		private final boolean system;
		private final @Nullable String sender;
		private final boolean overlay;

		public ChatReceive(Component message, boolean system, @Nullable String sender, boolean overlay) {
			this.message = message;
			this.system = system;
			this.sender = sender;
			this.overlay = overlay;
		}

		public Component message() {
			return this.message;
		}

		/** The message as plain text, without formatting. */
		public String text() {
			return this.message.getString();
		}

		public boolean system() {
			return this.system;
		}

		public @Nullable String sender() {
			return this.sender;
		}

		public boolean overlay() {
			return this.overlay;
		}
	}

	// ---- Player (client thread) ----

	/** The player died. Fired once, on the tick health reaches zero. */
	public record Death(Vec3 position, String dimension) {
	}

	/** The player is alive again after a death. */
	public record Respawn() {
		public static final Respawn INSTANCE = new Respawn();
	}

	// ---- Screens (client thread) ----

	/**
	 * A screen is about to open. Cancel to keep it from opening. Only screens opened explicitly are reported;
	 * the ones the game picks itself when a screen closes (title screen, death screen) are not: use Leave and Death.
	 */
	public static final class ScreenOpen extends Cancellable {
		private final Screen screen;

		public ScreenOpen(Screen screen) {
			this.screen = screen;
		}

		public Screen screen() {
			return this.screen;
		}
	}

	/** A screen is closing or being replaced by another. */
	public record ScreenClose(Screen screen) {
	}

	/**
	 * The connection to the server was lost or closed, with the reason the player sees. Fired before Leave.
	 * Client thread.
	 */
	public record Disconnect(Component reason) {
		public String text() {
			return this.reason.getString();
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
