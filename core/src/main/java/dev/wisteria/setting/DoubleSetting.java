package dev.wisteria.setting;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DoubleSetting extends NumberSetting<Double> {
	public DoubleSetting(String name, String description, double defaultValue, double min, double max, double step) {
		super(name, description, defaultValue, min, max, step);
	}

	@Override
	protected Double fromDouble(double value) {
		// Snapping to a step leaves binary noise (0.30000000000000004); keep what the user can actually see.
		return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).doubleValue();
	}

	@Override
	public String format() {
		return BigDecimal.valueOf(this.get()).stripTrailingZeros().toPlainString();
	}
}
