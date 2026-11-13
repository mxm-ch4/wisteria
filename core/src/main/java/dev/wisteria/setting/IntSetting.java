package dev.wisteria.setting;

public final class IntSetting extends NumberSetting<Integer> {
	public IntSetting(String name, String description, int defaultValue, int min, int max) {
		super(name, description, defaultValue, min, max, 1.0);
	}

	@Override
	protected Integer fromDouble(double value) {
		return (int)Math.round(value);
	}

	@Override
	public String format() {
		return Integer.toString(this.get());
	}
}
