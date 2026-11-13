package dev.wisteria.module;

import dev.wisteria.Client;
import dev.wisteria.compat.InputBackend;
import dev.wisteria.event.Events;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;

/** The registry of modules: lookup, categories, and toggling by bound key. */
public final class ModuleManager {
	private final Client client;
	private final Map<String, Module> modules = new LinkedHashMap<>();
	private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

	public ModuleManager(Client client) {
		this.client = client;
		client.bus().subscribe(Events.KeyInput.class, this::onKey);
	}

	public void register(Module module) {
		if (this.modules.putIfAbsent(module.id(), module) != null) {
			throw new IllegalStateException("Duplicate module id " + module.id());
		}

		module.attach(this.client, this::changed);
	}

	/** Case-insensitive lookup by name or id. */
	public @Nullable Module get(String name) {
		return this.modules.get(name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""));
	}

	public List<Module> all() {
		return Collections.unmodifiableList(new ArrayList<>(this.modules.values()));
	}

	public List<Module> in(Category category) {
		return this.modules.values().stream().filter(module -> module.category() == category).toList();
	}

	public List<Module> enabled() {
		return this.modules.values().stream().filter(Module::isEnabled).toList();
	}

	/** Called after any module is toggled, rebound or reconfigured. */
	public void onChange(Runnable listener) {
		this.changeListeners.add(listener);
	}

	private void changed() {
		for (Runnable listener : this.changeListeners) {
			listener.run();
		}
	}

	// A bind fires on a plain key press in the game itself, not while typing in chat or clicking through a menu.
	private void onKey(Events.KeyInput event) {
		if (event.action() != InputBackend.Action.PRESS || event.inScreen()) {
			return;
		}

		for (Module module : this.modules.values()) {
			if (module.bind().matches(event.key())) {
				this.client.setEnabled(module, !module.isEnabled());
			}
		}
	}
}
