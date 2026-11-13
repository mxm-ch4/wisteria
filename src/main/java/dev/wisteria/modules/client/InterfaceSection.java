package dev.wisteria.modules.client;

import dev.wisteria.setting.ChoiceSetting;
import dev.wisteria.setting.ColorSetting;
import dev.wisteria.setting.InputSetting;

/** Theme and accent colour of the interface. */
public final class InterfaceSection extends ClientSection {
	private final ChoiceSetting theme = this.add(new ChoiceSetting("Theme", "Interface preset", "", () -> this.client().themes().presetIds(), false));
	private final dev.wisteria.setting.BoolSetting brandedMenu = this.add(new dev.wisteria.setting.BoolSetting("Branded Menu",
		"The client's own main menu instead of the game's", true));

	public InterfaceSection() {
		super("Interface", "Theme and accent colour");
		this.add(new InputSetting("Accent", "Accent colour as #RRGGBB or #AARRGGBB", "#B79CFF", "Set",
			hex -> this.client().themes().useAccent(ColorSetting.fromHex(hex))));
		this.theme.onChange(id -> {
			if (!id.isEmpty() && !id.equals(this.client().themes().presetId())) {
				this.client().themes().usePreset(id);
			}
		});
	}

	public boolean brandedMenu() {
		return this.brandedMenu.get();
	}

	public void sync(String presetId) {
		this.theme.set(presetId);
	}
}
