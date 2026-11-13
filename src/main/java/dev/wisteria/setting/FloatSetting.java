package dev.wisteria.setting;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FloatSetting extends NumberSetting<Float> {
	public FloatSetting(String name, String description, float defaultValue, float min, float max, float step) {
		super(name, description, defaultValue, min, max, step);
	}

	@Override
	protected Float fromDouble(double value) {
		return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).floatValue();
	}

	@Override
	public String format() {
		return new BigDecimal(Float.toString(this.get())).stripTrailingZeros().toPlainString();
	}
}
