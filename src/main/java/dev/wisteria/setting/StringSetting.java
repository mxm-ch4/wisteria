package dev.wisteria.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class StringSetting extends Setting<String> {
	private final int maxLength;

	public StringSetting(String name, String description, String defaultValue, int maxLength) {
		super(name, description, defaultValue);
		this.maxLength = maxLength;
	}

	@Override
	protected String normalize(String value) {
		return value.length() > this.maxLength ? value.substring(0, this.maxLength) : value;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(this.get());
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
			this.set(json.getAsString());
		}
	}

	@Override
	public String format() {
		return this.get();
	}

	@Override
	public void parse(String input) {
		this.set(input);
	}
}
