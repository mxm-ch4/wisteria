package dev.wisteria.io;

/**
 * A token or key from {@code .minecraft/wisteria/.env}. It prints as {@code ****} wherever it ends up, so it cannot
 * leak into a log or the chat by accident. Hand it to {@link Net.Request#secret}: the value is put into the
 * request only when it is sent.
 */
public final class Secret {
	private final String name;
	private final String value;

	Secret(String name, String value) {
		this.name = name;
		this.value = value;
	}

	public String name() {
		return this.name;
	}

	String value() {
		return this.value;
	}

	@Override
	public String toString() {
		return this.name + "=****";
	}
}
