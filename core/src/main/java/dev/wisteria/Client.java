package dev.wisteria;

import dev.wisteria.command.CommandManager;
import dev.wisteria.compat.ClientListener;
import dev.wisteria.compat.PacketAdapter;
import dev.wisteria.compat.Platform;
import dev.wisteria.compat.Renderer2D;
import dev.wisteria.compat.Renderer3D;
import dev.wisteria.config.ConfigManager;
import dev.wisteria.event.EventBus;
import dev.wisteria.event.Events;
import dev.wisteria.module.Module;
import dev.wisteria.module.ModuleManager;
import dev.wisteria.module.impl.EntityEsp;
import dev.wisteria.module.impl.FullBright;
import dev.wisteria.module.impl.Watermark;
import dev.wisteria.social.Relations;

/** The running client: one per game session. Wires the game's callbacks to the event bus and owns the managers. */
public final class Client {
	private final Platform platform;
	private final EventBus bus = new EventBus();
	private final Relations relations = new Relations();
	private final ModuleManager modules;
	private final CommandManager commands;
	private final ConfigManager config;

	public Client(Platform platform) {
		this.platform = platform;
		this.modules = new ModuleManager(this);
		this.commands = new CommandManager(this);
		this.config = new ConfigManager(this, platform.gameDirectory().resolve(Wisteria.ID));

		this.modules.register(new FullBright());
		this.modules.register(new EntityEsp());
		this.modules.register(new Watermark());

		// A handler that throws takes its own module down, not the game.
		this.bus.onError((subscription, error) -> {
			Wisteria.LOG.error("Event handler failed", error);
			if (subscription.owner() instanceof Module module) {
				platform.execute(() -> {
					if (module.isEnabled()) {
						this.setEnabled(module, false);
						platform.chat().print(module.name() + " crashed and was switched off: " + error);
					}
				});
			}
		});

		this.config.load();
		this.modules.onChange(this.config::markDirty);
		this.relations.onChange(this.config::markDirty);
		this.commands.onChange(this.config::markDirty);
		this.bridge();
	}

	private void bridge() {
		this.platform.listen(new ClientListener() {
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
			public void onStop() {
				Client.this.config.save();
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
			Wisteria.LOG.error("{} failed to {}", module.name(), enabled ? "start" : "stop", e);
			this.platform.chat().print(module.name() + " could not be switched " + (enabled ? "on" : "off") + ": " + e);
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

	public ModuleManager modules() {
		return this.modules;
	}

	public CommandManager commands() {
		return this.commands;
	}

	public ConfigManager config() {
		return this.config;
	}
}
