package dev.wisteria.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Locale;

public final class BoolSetting extends Setting<Boolean> {
	public BoolSetting(String name, String description, boolean defaultValue) {
		super(name, description, defaultValue);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(this.get());
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) {
			this.set(json.getAsBoolean());
		}
	}

	@Override
	public String format() {
		return this.get() ? "on" : "off";
	}

	@Override
	public void parse(String input) {
		switch (input.trim().toLowerCase(Locale.ROOT)) {
			case "on", "true", "yes", "1" -> this.set(true);
			case "off", "false", "no", "0" -> this.set(false);
			case "toggle" -> this.set(!this.get());
			default -> throw new IllegalArgumentException("Expected on or off, got \"" + input + "\"");
		}
	}

	@Override
	public List<String> suggestions() {
		return List.of("on", "off", "toggle");
	}
}
