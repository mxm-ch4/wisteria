package dev.wisteria.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A number on a slider: bounded, optionally snapped to a step. */
public abstract class NumberSetting<N extends Number> extends Setting<N> {
	private final double min;
	private final double max;
	private final double step;

	protected NumberSetting(String name, String description, N defaultValue, double min, double max, double step) {
		super(name, description, defaultValue);
		if (!(min <= max) || step < 0.0) {
			throw new IllegalArgumentException("Bad range for " + name + ": " + min + ".." + max + " step " + step);
		}

		this.min = min;
		this.max = max;
		this.step = step;
	}

	public final double min() {
		return this.min;
	}

	public final double max() {
		return this.max;
	}

	/** Zero means any value in range is allowed. */
	public final double step() {
		return this.step;
	}

	protected abstract N fromDouble(double value);

	@Override
	protected final N normalize(N value) {
		double number = value.doubleValue();
		if (Double.isNaN(number)) {
			return this.get();
		}

		number = Math.max(this.min, Math.min(this.max, number));
		if (this.step > 0.0) {
			number = this.min + Math.round((number - this.min) / this.step) * this.step;
			number = Math.max(this.min, Math.min(this.max, number));
		}

		return this.fromDouble(number);
	}

	/** Position of the value on its slider, 0..1. */
	public final double fraction() {
		return this.max == this.min ? 0.0 : (this.get().doubleValue() - this.min) / (this.max - this.min);
	}

	public final void setFraction(double fraction) {
		this.set(this.fromDouble(this.min + Math.max(0.0, Math.min(1.0, fraction)) * (this.max - this.min)));
	}

	@Override
	public final JsonElement toJson() {
		return new JsonPrimitive(this.get());
	}

	@Override
	public final void fromJson(JsonElement json) {
		if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			this.set(this.fromDouble(json.getAsDouble()));
		}
	}

	@Override
	public final void parse(String input) {
		double number;
		try {
			number = Double.parseDouble(input.trim().replace(',', '.'));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Expected a number, got \"" + input + "\"");
		}

		if (Double.isNaN(number) || Double.isInfinite(number)) {
			throw new IllegalArgumentException("Expected a number, got \"" + input + "\"");
		}

		this.set(this.fromDouble(number));
	}
}
