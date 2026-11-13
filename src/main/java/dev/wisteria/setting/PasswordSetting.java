package dev.wisteria.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A secret typed into a masked field: handed straight to its store (the secrets file) and never kept here, never
 * saved in a profile, never shown, never accepted from chat.
 */
public class PasswordSetting extends Setting<Integer> {
	private final Consumer<String> store;
	private final BooleanSupplier isSet;

	public PasswordSetting(String name, String description, BooleanSupplier isSet, Consumer<String> store) {
		super(name, description, 0);
		this.store = store;
		this.isSet = isSet;
	}

	/** From the masked field only. An empty value clears it. */
	public final void store(String secret) {
		this.set(this.get() + 1);
		this.store.accept(secret);
	}

	public final boolean isStored() {
		return this.isSet.getAsBoolean();
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
		return this.isStored() ? "****" : "(not set)";
	}

	@Override
	public void parse(String input) {
		throw new IllegalArgumentException(this.name() + " can only be typed into its field in the menu, never into chat");
	}
}
