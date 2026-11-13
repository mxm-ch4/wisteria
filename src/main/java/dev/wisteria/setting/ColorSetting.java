package dev.wisteria.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.Locale;

/** An ARGB colour. Written as #AARRGGBB; #RRGGBB is accepted and means fully opaque. */
public final class ColorSetting extends Setting<Integer> {
	public ColorSetting(String name, String description, int defaultArgb) {
		super(name, description, defaultArgb);
	}

	public int alpha() {
		return this.get() >>> 24;
	}

	/** The colour with its alpha replaced. */
	public int withAlpha(int alpha) {
		return (this.get() & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
	}

	public static String toHex(int argb) {
		return String.format(Locale.ROOT, "#%08X", argb);
	}

	/** @throws IllegalArgumentException if the text is not #RRGGBB or #AARRGGBB */
	public static int fromHex(String text) {
		String hex = text.trim();
		if (hex.startsWith("#")) {
			hex = hex.substring(1);
		}

		if (!hex.matches("[0-9a-fA-F]{6}|[0-9a-fA-F]{8}")) {
			throw new IllegalArgumentException("Expected a colour like #B79CFF or #FFB79CFF, got \"" + text + "\"");
		}

		int value = (int)Long.parseLong(hex, 16);
		return hex.length() == 6 ? value | 0xFF000000 : value;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(toHex(this.get()));
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
			try {
				this.set(fromHex(json.getAsString()));
			} catch (IllegalArgumentException ignored) {
				// keep the current value
			}
		}
	}

	@Override
	public String format() {
		return toHex(this.get());
	}

	@Override
	public void parse(String input) {
		this.set(fromHex(input));
	}
}
