package dev.wisteria.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import java.util.function.Supplier;

/** A line that shows something live (a counter, a status); nothing to set, never saved. */
public class InfoSetting extends Setting<Integer> {
	private final Supplier<String> text;

	public InfoSetting(String name, String description, Supplier<String> text) {
		super(name, description, 0);
		this.text = text;
	}

	@Override
	public JsonElement toJson() {
		return JsonNull.INSTANCE;
	}

	@Override
	public void fromJson(JsonElement json) {
	}

	@Override
	public boolean persistent() {
		return false;
	}

	@Override
	public String format() {
		return this.text.get();
	}

	@Override
	public void parse(String input) {
		throw new IllegalArgumentException(this.name() + " only shows information");
	}
}
