package dev.wisteria;

import dev.wisteria.command.CommandManager;
import dev.wisteria.platform.ClientListener;
import dev.wisteria.platform.PacketAdapter;
import dev.wisteria.platform.Platform;
import dev.wisteria.render.Renderer2D;
import dev.wisteria.render.Renderer3D;
import dev.wisteria.config.ConfigManager;
import dev.wisteria.event.EventBus;
import dev.wisteria.event.Events;
import dev.wisteria.io.Net;
import dev.wisteria.io.Secrets;
import dev.wisteria.io.Storage;
import java.nio.file.Path;
import dev.wisteria.module.Module;
import dev.wisteria.module.ModuleManager;
import dev.wisteria.modules.Modules;
import java.util.List;
import dev.wisteria.social.Relations;
import dev.wisteria.ui.SplashScreen;
import dev.wisteria.ui.ThemeManager;
import dev.wisteria.ui.gui.GuiState;

/** The running client: one per game session. Wires the game's callbacks to the event bus and owns the managers. */
public final class Client {
	private final Platform platform;
	private final EventBus bus = new EventBus();
	private final Relations relations = new Relations();
	private final ThemeManager themes = new ThemeManager();
	private final GuiState gui = new GuiState();
	private volatile boolean ready;
	private final ModuleManager modules;
	private final CommandManager commands;
	private final ConfigManager config;
	private final Path root;
	private final Secrets secrets;
	private final Net net;
	private final dev.wisteria.hud.HudManager hud;

	public Client(Platform platform) {
		this(platform, Modules.create());
	}

	/** @param modules what to register instead of the shipped set; tests use this to add the module under test */
	public Client(Platform platform, List<Module> modules) {
		this.platform = platform;
		this.root = platform.gameDirectory().resolve(Wisteria.ID);
		this.secrets = new Secrets(this.root);
		this.net = new Net(this.secrets);
		this.modules = new ModuleManager(this);
		this.commands = new CommandManager(this);
		this.config = new ConfigManager(this, this.root);

		modules.forEach(this.modules::register);
		this.hud = new dev.wisteria.hud.HudManager(this);

		// A handler that throws takes its own module down, not the game.
		this.bus.onError((subscription, error) -> {
			Wisteria.LOG.error("Event handler failed", this.secrets.redact(error));
			if (subscription.owner() instanceof Module module) {
				platform.execute(() -> {
					if (module.isEnabled()) {
						this.setEnabled(module, false);
						platform.chat().print(module.name() + " crashed and was switched off: " + this.secrets.redact(error.toString()));
					}
				});
			}
		});

		this.config.load();
		this.modules.onChange(this.config::markDirty);
		this.relations.onChange(this.config::markDirty);
		this.commands.onChange(this.config::markDirty);
		this.themes.onChange(this.config::markDirty);
		this.gui.onChange(this.config::markDirty);
		this.hud.onChange(this.config::markDirty);
		this.net.onChange(this.config::markDirty);
		this.net.onSuspend((id, reason) -> platform.execute(() -> {
			Module module = this.modules.get(id);
			platform.chat().print((module != null ? module.name() : id) + ": " + reason);
		}));
		this.bridge();
	}

	private void bridge() {
		this.platform.listen(new ClientListener() {
			@Override
			public void onTickStart() {
				Client.this.bus.post(Events.TickStart.INSTANCE);
			}

			@Override
			public void onJoin() {
				Client.this.bus.post(Events.Join.INSTANCE);
			}

			@Override
			public void onLeave() {
				Client.this.bus.post(Events.Leave.INSTANCE);
			}

			@Override
			public void onTick() {
				Client.this.bus.post(Events.Tick.INSTANCE);
				Client.this.config.tick();
			}

			@Override
			public void onRender2D(Renderer2D renderer, float tickDelta) {
				Client.this.bus.post(new Events.Render2D(renderer, tickDelta));
			}

			@Override
			public void onRender3D(Renderer3D renderer, float tickDelta) {
				Client.this.bus.post(new Events.Render3D(renderer, tickDelta));
			}

			@Override
			public void onTitleScreen() {
				Client.this.ready = true;
				Client.this.platform.screens().open(new SplashScreen(Client.this));
			}

			@Override
			public void onStop() {
				Client.this.config.save();
				Client.this.net.close();
			}
		});

		this.platform.input().listen((key, action, shift, control, alt, inScreen) ->
			this.bus.post(new Events.KeyInput(key, action, shift, control, alt, inScreen)));

		this.platform.packets().listen(new PacketAdapter.PacketListener() {
			@Override
			public boolean onSend(PacketAdapter.PacketView packet) {
				return Client.this.bus.post(new Events.PacketSend(packet)).isCancelled();
			}

			@Override
			public boolean onReceive(PacketAdapter.PacketView packet) {
				return Client.this.bus.post(new Events.PacketReceive(packet)).isCancelled();
			}
		});

		this.platform.chat().listen(this.commands::handleChat);
		this.platform.chat().completions(this.commands::complete);
	}

	/**
	 * Switches a module on or off without letting a failure escape: a module that cannot start is reported
	 * in chat and stays off.
	 *
	 * @return true if the module ended up in the requested state
	 */
	public boolean setEnabled(Module module, boolean enabled) {
		try {
			module.setEnabled(enabled);
			return true;
		} catch (RuntimeException e) {
			Wisteria.LOG.error("{} failed to {}", module.name(), enabled ? "start" : "stop", this.secrets.redact(e));
			this.platform.chat().print(module.name() + " could not be switched " + (enabled ? "on" : "off") + ": " + this.secrets.redact(e.toString()));
			return false;
		}
	}

	public Platform platform() {
		return this.platform;
	}

	public EventBus bus() {
		return this.bus;
	}

	public Relations relations() {
		return this.relations;
	}

	/** True once the game has finished loading and reached the title screen for the first time. */
	public boolean isReady() {
		return this.ready;
	}

	public GuiState gui() {
		return this.gui;
	}

	public ThemeManager themes() {
		return this.themes;
	}

	public ModuleManager modules() {
		return this.modules;
	}

	public CommandManager commands() {
		return this.commands;
	}

	public ConfigManager config() {
		return this.config;
	}

	/** {@code .minecraft/wisteria}: everything the client keeps on disk lives under it. */
	public Path root() {
		return this.root;
	}

	/** The folder of one module; modules use {@code Module.storage()}. */
	public Storage storage(Module module) {
		return new Storage(this.root, module.id());
	}

	/** The HUD: element placements, drawing, notifications. */
	public dev.wisteria.hud.HudManager hud() {
		return this.hud;
	}

	public Secrets secrets() {
		return this.secrets;
	}

	public Net net() {
		return this.net;
	}
}
