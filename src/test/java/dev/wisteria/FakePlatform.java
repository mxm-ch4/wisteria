package dev.wisteria;

import dev.wisteria.platform.AudioBackend;
import dev.wisteria.platform.ChatBackend;
import dev.wisteria.platform.ClientListener;
import dev.wisteria.platform.InputBackend;
import dev.wisteria.platform.Key;
import dev.wisteria.platform.PacketAdapter;
import dev.wisteria.platform.Platform;
import dev.wisteria.platform.ScreenBackend;
import dev.wisteria.platform.VisualBackend;
import dev.wisteria.platform.WorldBackend;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** A game that is not there: records what the client asks of it and lets tests play the game's part. */
public final class FakePlatform implements Platform {
	public final List<String> chatLines = new ArrayList<>();
	public final List<WorldBackend.Entity> entities = new ArrayList<>();
	public float brightness;
	public boolean worldLoaded = true;
	public String clipboard = "";
	public ScreenBackend.ClientScreen openScreen;
	private final Path directory;
	private final List<ClientListener> listeners = new ArrayList<>();
	private final List<InputBackend.InputListener> inputListeners = new ArrayList<>();
	private final List<PacketAdapter.PacketListener> packetListeners = new ArrayList<>();
	private final List<ChatBackend.ChatListener> chatListeners = new ArrayList<>();
	private ChatBackend.CompletionProvider completions;

	public FakePlatform(Path directory) {
		this.directory = directory;
	}

	// ---- the game's side, driven by tests ----

	public void tick(int times) {
		for (int i = 0; i < times; i++) {
			this.listeners.forEach(ClientListener::onTick);
		}
	}

	/** One HUD frame, as the game draws it over the world. */
	public void renderHud(dev.wisteria.render.Renderer2D renderer) {
		this.listeners.forEach(listener -> listener.onRender2D(renderer, 1.0F));
	}

		public void stop() {
		this.listeners.forEach(ClientListener::onStop);
	}

	public void press(Key key, boolean inScreen) {
		this.inputListeners.forEach(listener -> listener.onKey(key, InputBackend.Action.PRESS, false, false, false, inScreen));
		this.inputListeners.forEach(listener -> listener.onKey(key, InputBackend.Action.RELEASE, false, false, false, inScreen));
	}

	/** @return true if the message was swallowed, i.e. would not reach the server */
	public boolean typeInChat(String message) {
		boolean swallowed = false;
		for (ChatBackend.ChatListener listener : this.chatListeners) {
			swallowed |= listener.onSend(message);
		}

		return swallowed;
	}

	public ChatBackend.Completion tab(String input) {
		return this.completions.complete(input);
	}

	public boolean send(String packetName) {
		boolean cancelled = false;
		for (PacketAdapter.PacketListener listener : this.packetListeners) {
			cancelled |= listener.onSend(new FakePacket(packetName));
		}

		return cancelled;
	}

	public String lastChatLine() {
		return this.chatLines.isEmpty() ? "" : this.chatLines.get(this.chatLines.size() - 1);
	}

	/** Stands in for a game packet in tests: only the name is real. */
	private record FakePacket(String name) implements PacketAdapter.PacketView {
		@Override
		public net.minecraft.network.protocol.Packet<?> packet() {
			throw new UnsupportedOperationException("no game packets in unit tests");
		}
	}

	// ---- Platform ----

	@Override
	public String minecraftVersion() {
		return "test";
	}

	@Override
	public String graphicsBackend() {
		return "none";
	}

	@Override
	public Path gameDirectory() {
		return this.directory;
	}

	@Override
	public void execute(Runnable task) {
		task.run();
	}

	@Override
	public void listen(ClientListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public InputBackend input() {
		return new InputBackend() {
			@Override
			public boolean isDown(Key key) {
				return false;
			}

			@Override
			public void listen(InputListener listener) {
				FakePlatform.this.inputListeners.add(listener);
			}

			@Override
			public String clipboard() {
				return FakePlatform.this.clipboard;
			}

			@Override
			public void setClipboard(String text) {
				FakePlatform.this.clipboard = text;
			}
		};
	}

	@Override
	public AudioBackend audio() {
		return (source, volume) -> {
			throw new UnsupportedOperationException();
		};
	}

	@Override
	public PacketAdapter packets() {
		return this.packetListeners::add;
	}

	@Override
	public ScreenBackend screens() {
		return new ScreenBackend() {
			@Override
			public void open(ClientScreen screen) {
				FakePlatform.this.openScreen = screen;
			}

			@Override
			public void close() {
				ClientScreen screen = FakePlatform.this.openScreen;
				FakePlatform.this.openScreen = null;
				if (screen != null) {
					screen.onClose();
				}
			}

			@Override
			public boolean isAnyOpen() {
				return FakePlatform.this.openScreen != null;
			}
		};
	}

	@Override
	public ChatBackend chat() {
		return new ChatBackend() {
			@Override
			public void print(String message) {
				FakePlatform.this.chatLines.add(message);
			}

			@Override
			public void listen(ChatListener listener) {
				FakePlatform.this.chatListeners.add(listener);
			}

			@Override
			public void completions(CompletionProvider provider) {
				FakePlatform.this.completions = provider;
			}
		};
	}

	@Override
	public WorldBackend world() {
		return new WorldBackend() {
			@Override
			public boolean isLoaded() {
				return FakePlatform.this.worldLoaded;
			}

			@Override
			public List<Entity> entities(float tickDelta) {
				return List.copyOf(FakePlatform.this.entities);
			}
		};
	}

	@Override
	public VisualBackend visuals() {
		return value -> this.brightness = value;
	}
}
