package dev.wisteria.setting;

import com.google.gson.JsonElement;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * One configurable value of a module. Knows how to validate itself, how to go to and from JSON (config files)
 * and to and from text (chat commands, later the GUI's text fields).
 */
public abstract class Setting<T> {
	private final String name;
	private final String id;
	private final String description;
	private final T defaultValue;
	private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
	private volatile T value;

	protected Setting(String name, String description, T defaultValue) {
		this.name = name;
		this.id = toId(name);
		this.description = description;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	/** "Line Width" -> "line-width": the key in config files and in commands. */
	public static String toId(String name) {
		return name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
	}

	public final String name() {
		return this.name;
	}

	public final String id() {
		return this.id;
	}

	public final String description() {
		return this.description;
	}

	public final T defaultValue() {
		return this.defaultValue;
	}

	public final T get() {
		return this.value;
	}

	/**
	 * Sets the value after {@link #normalize normalising} it (clamping, snapping to a step).
	 *
	 * @return true if the stored value changed
	 */
	public final boolean set(T value) {
		T normalized = this.normalize(value);
		if (Objects.equals(this.value, normalized)) {
			return false;
		}

		this.value = normalized;
		for (Consumer<T> listener : this.listeners) {
			listener.accept(normalized);
		}

		return true;
	}

	public final void reset() {
		this.set(this.defaultValue);
	}

	/** Called after every change, on the thread that made it. */
	public final void onChange(Consumer<T> listener) {
		this.listeners.add(listener);
	}

	/** Brings a requested value into the allowed range. The default accepts anything. */
	protected T normalize(T value) {
		return value;
	}

	public abstract JsonElement toJson();

	/** Applies a value read from a config file. Anything unreadable is ignored: the current value stays. */
	public abstract void fromJson(JsonElement json);

	/** The value as shown to the user. */
	public abstract String format();

	/**
	 * Sets the value from user input.
	 *
	 * @throws IllegalArgumentException with a message fit to show to the user
	 */
	public abstract void parse(String input);

	/** Values worth offering as completions; empty when the value is free-form. */
	public List<String> suggestions() {
		return List.of();
	}
}
