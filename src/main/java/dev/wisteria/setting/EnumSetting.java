package dev.wisteria.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** One of a fixed set of modes. Stored and typed as the lower-case constant name with dashes. */
public final class EnumSetting<E extends Enum<E>> extends Setting<E> {
	private final E[] values;

	public EnumSetting(String name, String description, E defaultValue) {
		super(name, description, defaultValue);
		this.values = defaultValue.getDeclaringClass().getEnumConstants();
	}

	public List<E> values() {
		return List.of(this.values);
	}

	/** Advances to the next mode, wrapping around. */
	public void cycle() {
		this.set(this.values[(this.get().ordinal() + 1) % this.values.length]);
	}

	private static String key(Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
	}

	private @Nullable E byKey(String key) {
		String wanted = key.trim().toLowerCase(Locale.ROOT).replace('_', '-');
		for (E value : this.values) {
			if (key(value).equals(wanted)) {
				return value;
			}
		}

		return null;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(key(this.get()));
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
			E value = this.byKey(json.getAsString());
			if (value != null) {
				this.set(value);
			}
		}
	}

	@Override
	public String format() {
		return key(this.get());
	}

	@Override
	public void parse(String input) {
		E value = this.byKey(input);
		if (value == null) {
			throw new IllegalArgumentException("Expected one of " + this.suggestions() + ", got \"" + input + "\"");
		}

		this.set(value);
	}

	@Override
	public List<String> suggestions() {
		return Arrays.stream(this.values).map(EnumSetting::key).toList();
	}
}
