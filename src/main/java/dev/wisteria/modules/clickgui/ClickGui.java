package dev.wisteria.modules.clickgui;

import dev.wisteria.platform.Key;
import dev.wisteria.module.Category;
import dev.wisteria.module.Module;
import dev.wisteria.ui.gui.ClickGuiScreen;

/** Opens the module browser. "Enabled" simply means the screen is up, so it is never restored at startup. */
public final class ClickGui extends Module {
	private ClickGuiScreen screen;

	public ClickGui() {
		super("ClickGUI", "Browse modules and change their settings with the mouse", Category.MISC, Key.RIGHT_SHIFT);
	}

	@Override
	protected boolean remembersEnabled() {
		return false;
	}

	@Override
	protected void onEnable() {
		this.screen = new ClickGuiScreen(this.client(), () -> this.client().setEnabled(this, false));
		this.client().platform().screens().open(this.screen);
	}

	@Override
	protected void onDisable() {
		this.screen = null;
		this.client().platform().screens().close();
	}

	/** The screen while it is open, for diagnostics. */
	public ClickGuiScreen screen() {
		return this.screen;
	}
}
