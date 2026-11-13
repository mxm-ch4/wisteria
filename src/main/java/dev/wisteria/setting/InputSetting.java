package dev.wisteria.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import java.util.function.Consumer;

/**
 * A text field with a button: the button (or Enter) hands what was typed to an action, e.g. "add a friend",
 * "save the profile as". The action throws IllegalArgumentException with a message for the user when the input
 * makes no sense. Never saved; its value counts presses, so the GUI can see it respond.
 */
public class InputSetting extends Setting<Integer> {
	private final String placeholder;
	private final String button;
	private final Consumer<String> action;
	private volatile String lastError = "";

	public InputSetting(String name, String description, String placeholder, String button, Consumer<String> action) {
		super(name, description, 0);
		this.placeholder = placeholder;
		this.button = button;
		this.action = action;
	}

	public final String placeholder() {
		return this.placeholder;
	}

	public final String button() {
		return this.button;
	}

	/** What went wrong with the last submit, or empty. */
	public final String lastError() {
		return this.lastError;
	}

	/** @return true if the action accepted the text */
	public final boolean submit(String text) {
		this.set(this.get() + 1);
		try {
			this.action.accept(text.strip());
			this.lastError = "";
			return true;
		} catch (IllegalArgumentException e) {
			this.lastError = e.getMessage() == null ? "Not accepted" : e.getMessage();
			return false;
		}
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
		return this.button;
	}

	@Override
	public void parse(String input) {
		if (!this.submit(input)) {
			throw new IllegalArgumentException(this.lastError);
		}
	}
}
