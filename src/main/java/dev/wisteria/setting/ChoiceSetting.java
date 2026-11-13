package dev.wisteria.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.function.Supplier;

/** One of a list that changes at run time (configured proxies, saved profiles). An empty value means none. */
public class ChoiceSetting extends Setting<String> {
	private final Supplier<List<String>> options;
	private final boolean persistent;

	public ChoiceSetting(String name, String description, String defaultValue, Supplier<List<String>> options, boolean persistent) {
		super(name, description, defaultValue);
		this.options = options;
		this.persistent = persistent;
	}

	@Override
	public boolean persistent() {
		return this.persistent;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(this.get());
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive()) {
			this.set(json.getAsString());
		}
	}

	@Override
	public String format() {
		return this.get().isEmpty() ? "none" : this.get();
	}

	@Override
	public void parse(String input) {
		String wanted = input.strip();
		if (wanted.equalsIgnoreCase("none")) {
			this.set("");
			return;
		}

		for (String option : this.options.get()) {
			if (option.equalsIgnoreCase(wanted)) {
				this.set(option);
				return;
			}
		}

		throw new IllegalArgumentException("\"" + wanted + "\" is not one of: " + String.join(", ", this.options.get()));
	}

	/** The options, with "none" first when nothing chosen is allowed (an empty default). */
	@Override
	public List<String> suggestions() {
		if (!this.defaultValue().isEmpty() || !this.persistent) {
			return this.options.get();
		}

		List<String> all = new java.util.ArrayList<>();
		all.add("none");
		all.addAll(this.options.get());
		return all;
	}
}
