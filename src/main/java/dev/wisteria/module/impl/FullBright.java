package dev.wisteria.module.impl;

import dev.wisteria.module.Category;
import dev.wisteria.module.Module;
import dev.wisteria.setting.DoubleSetting;

/** Lights the world evenly by overriding the brightness the lightmap is built with. Visual only. */
public final class FullBright extends Module {
	private final DoubleSetting brightness = this.add(new DoubleSetting(
		"Brightness", "1 is the game's maximum, 16 lights everything fully", 16.0, 1.0, 16.0, 0.5));

	public FullBright() {
		super("FullBright", "See in the dark without torches or potions", Category.RENDER);
		this.brightness.onChange(value -> {
			if (this.isEnabled()) {
				this.apply();
			}
		});
	}

	private void apply() {
		this.client().platform().visuals().setBrightness(this.brightness.get().floatValue());
	}

	@Override
	protected void onEnable() {
		this.apply();
	}

	@Override
	protected void onDisable() {
		this.client().platform().visuals().setBrightness(0.0F);
	}
}
