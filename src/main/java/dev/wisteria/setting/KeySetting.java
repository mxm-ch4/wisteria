package dev.wisteria.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import dev.wisteria.platform.Key;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A key binding, possibly unbound. Stored by key name ("G", "RIGHT_SHIFT"), never by key code:
 * codes differ between Minecraft versions.
 */
public final class KeySetting extends Setting<Optional<Key>> {
	public static final String NONE = "none";

	public KeySetting(String name, String description) {
		super(name, description, Optional.empty());
	}

	public KeySetting(String name, String description, Key defaultKey) {
		super(name, description, Optional.of(defaultKey));
	}

	public boolean matches(Key key) {
		return this.get().filter(bound -> bound == key).isPresent();
	}

	@Override
	public JsonElement toJson() {
		return this.get().<JsonElement>map(key -> new JsonPrimitive(key.name())).orElse(JsonNull.INSTANCE);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonNull()) {
			this.set(Optional.empty());
		} else if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
			Key key = Key.byName(json.getAsString());
			if (key != null) {
				this.set(Optional.of(key));
			}
		}
	}

	@Override
	public String format() {
		return this.get().map(key -> key.name().toLowerCase(Locale.ROOT)).orElse(NONE);
	}

	@Override
	public void parse(String input) {
		if (input.trim().equalsIgnoreCase(NONE)) {
			this.set(Optional.empty());
			return;
		}

		Key key = Key.byName(input);
		if (key == null) {
			throw new IllegalArgumentException("Unknown key \"" + input + "\"; use a name like g, f6, right_shift or none");
		}

		this.set(Optional.of(key));
	}

	@Override
	public List<String> suggestions() {
		List<String> names = new ArrayList<>();
		names.add(NONE);
		for (Key key : Key.values()) {
			names.add(key.name().toLowerCase(Locale.ROOT));
		}

		return names;
	}
}
