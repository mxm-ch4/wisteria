package dev.wisteria.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/**
 * A button: does something when pressed (in the GUI, or as {@code .module name} in chat). Holds no value and is
 * never saved; its "value" is how many times it was pressed, so the GUI and its click test can see it respond.
 */
public class ActionSetting extends Setting<Integer> {
	private final Runnable action;
	private final String button;
	private @org.jspecify.annotations.Nullable String note;

	public ActionSetting(String name, String description, String button, Runnable action) {
		super(name, description, 0);
		this.action = action;
		this.button = button;
	}

	/**
	 * Marks a button the GUI's click test must not press (it opens another screen, or undoes something the test
	 * depends on), with the reason; such buttons have their own tests.
	 */
	public final ActionSetting withNote(String note) {
		this.note = note;
		return this;
	}

	public final @org.jspecify.annotations.Nullable String note() {
		return this.note;
	}

	/** The label on the button. */
	public final String button() {
		return this.button;
	}

	public final void press() {
		this.set(this.get() + 1);
		this.action.run();
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
		this.press();
	}
}
