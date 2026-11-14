package dev.wisteria.module;

import dev.wisteria.Client;
import dev.wisteria.io.Net;
import dev.wisteria.io.Secret;
import dev.wisteria.io.Storage;
import dev.wisteria.platform.Platform;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import dev.wisteria.platform.Key;
import dev.wisteria.event.EventBus;
import dev.wisteria.setting.KeySetting;
import dev.wisteria.setting.Setting;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * One switchable feature. A module declares its settings and event handlers in its constructor;
 * the handlers only receive events while the module is enabled.
 */
public abstract class Module {
	private final String name;
	private final String id;
	private final String description;
	private final Category category;
	private final KeySetting bind;
	private final Map<String, Setting<?>> settings = new LinkedHashMap<>();
	private final List<Handler<?>> handlers = new ArrayList<>();
	private final List<EventBus.Subscription<?>> subscriptions = new ArrayList<>();
	private @Nullable Client client;
	private @Nullable Runnable changeListener;
	private boolean enabled;

	protected Module(String name, String description, Category category) {
		this(name, description, category, null);
	}

	/** @param defaultBind the key the module is bound to until the user changes it; null for none */
	protected Module(String name, String description, Category category, @Nullable Key defaultBind) {
		this.bind = defaultBind != null
			? new KeySetting("Bind", "Key that toggles this module", defaultBind)
			: new KeySetting("Bind", "Key that toggles this module");
		this.name = name;
		this.id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
		this.description = description;
		this.category = category;
		this.bind.onChange(key -> this.changed());
	}

	public final String name() {
		return this.name;
	}

	/** Lower-case name without spaces: the key in config files and in commands. */
	public final String id() {
		return this.id;
	}

	public final String description() {
		return this.description;
	}

	public final Category category() {
		return this.category;
	}

	public final KeySetting bind() {
		return this.bind;
	}

	public final Collection<Setting<?>> settings() {
		return Collections.unmodifiableCollection(this.settings.values());
	}

	public final @Nullable Setting<?> setting(String id) {
		return this.settings.get(Setting.toId(id));
	}

	public final boolean isEnabled() {
		return this.enabled;
	}

	/** Registers a setting; call from the constructor. Returns it for assignment to a field. */
	protected final <S extends Setting<?>> S add(S setting) {
		if (this.settings.putIfAbsent(setting.id(), setting) != null) {
			throw new IllegalStateException(this.name + " already has a setting called " + setting.id());
		}

		setting.onChange(value -> this.changed());
		return setting;
	}

	/** Subscribes to an event for as long as the module is enabled; call from the constructor. */
	protected final <E> void on(Class<E> type, Consumer<E> handler) {
		this.handlers.add(new Handler<>(type, handler));
	}

	/** The game. Call it on the client thread. Null only in unit tests, where there is no game. */
	protected final Minecraft mc() {
		return Minecraft.getInstance();
	}

	/** The player, or null when not in a world (and in unit tests). Check it before acting. */
	protected final @Nullable LocalPlayer player() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft != null ? minecraft.player : null;
	}

	/** The world the player is in, or null when not in one (and in unit tests). */
	protected final @Nullable ClientLevel level() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft != null ? minecraft.level : null;
	}

	/** Shorthand for {@code client().platform()}. */
	protected final Platform platform() {
		return this.client().platform();
	}

	/** Prints a line in chat, prefixed with the module's name. Client-side only; nothing is sent. */
	protected final void info(String message) {
		Client current = this.client();
		current.platform().chat().print(this.name + ": " + current.secrets().redact(message));
	}

	/** This module's folder, {@code .minecraft/wisteria/data/<id>/}: the only place it reads and writes files. */
	protected final Storage storage() {
		return this.client().storage(this);
	}

	/**
	 * A token or key from {@code .minecraft/wisteria/.env}; empty if the user has not put it there.
	 *
	 * @throws SecurityException for modules without the SECRETS permission
	 */
	protected final Optional<Secret> secret(String name) {
		Client current = this.client();
		return current.secrets().get(this.id, name, current.config().profile());
	}

	/**
	 * Sends an HTTPS request to a host the user allowed. Completes off the client thread.
	 *
	 * @throws SecurityException for modules without the NET permission
	 */
	protected final CompletableFuture<Net.Response> send(Net.Request request) {
		return this.client().net().send(this.id, request);
	}

	/** The running client. Only valid once the module is registered, so not in the constructor. */
	protected final Client client() {
		Client current = this.client;
		if (current == null) {
			throw new IllegalStateException(this.name + " is not registered yet");
		}

		return current;
	}

	/** False for groups of client controls that have no on and off: their row only opens. */
	protected boolean toggleable() {
		return true;
	}

	public final boolean isToggleable() {
		return this.toggleable();
	}

	/** False for modules whose "enabled" is a momentary state, like an open screen: they always start off. */
	protected boolean remembersEnabled() {
		return true;
	}

	public final boolean savesEnabled() {
		return this.remembersEnabled();
	}

	protected void onEnable() {
	}

	/** Clear My Data wiped the data folder: forget anything kept in memory from it (caches, lists). */
	protected void onDataCleared() {
	}

	/** Called by the client after Clear My Data. */
	public final void dataCleared() {
		this.onDataCleared();
	}

	protected void onDisable() {
	}

	final void attach(Client client, Runnable changeListener) {
		this.client = client;
		this.changeListener = changeListener;
	}

	public final void toggle() {
		this.setEnabled(!this.enabled);
	}

	/** Client thread only. A module whose {@link #onEnable()} throws stays disabled. */
	public final void setEnabled(boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}

		Client current = this.client();
		if (enabled) {
			this.enabled = true;
			for (Handler<?> handler : this.handlers) {
				this.subscriptions.add(handler.subscribe(current.bus(), this));
			}

			try {
				this.onEnable();
			} catch (RuntimeException e) {
				this.unsubscribe(current);
				this.enabled = false;
				throw e;
			}
		} else {
			this.enabled = false;
			this.unsubscribe(current);
			this.onDisable();
		}

		this.changed();
	}

	private void unsubscribe(Client current) {
		for (EventBus.Subscription<?> subscription : this.subscriptions) {
			current.bus().unsubscribe(subscription);
		}

		this.subscriptions.clear();
	}

	/** Back to factory state: disabled, default bind, default settings. */
	public final void reset() {
		this.setEnabled(false);
		this.bind.reset();
		for (Setting<?> setting : this.settings.values()) {
			setting.reset();
		}
	}

	private void changed() {
		Runnable listener = this.changeListener;
		if (listener != null) {
			listener.run();
		}
	}

	@Override
	public String toString() {
		return this.name;
	}

	private record Handler<E>(Class<E> type, Consumer<E> handler) {
		EventBus.Subscription<E> subscribe(EventBus bus, Module owner) {
			return bus.subscribe(this.type, this.handler, owner);
		}
	}
}
