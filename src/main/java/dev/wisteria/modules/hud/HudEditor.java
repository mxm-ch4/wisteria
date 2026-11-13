package dev.wisteria.modules.hud;

import dev.wisteria.hud.HudEditorScreen;
import dev.wisteria.module.Category;
import dev.wisteria.module.Module;
import org.jspecify.annotations.Nullable;

/** Opens the HUD editor. Like the ClickGUI, "on" just means the screen is up. */
public final class HudEditor extends Module {
	private @Nullable HudEditorScreen screen;

	public HudEditor() {
		super("HUD Editor", "Drag HUD elements into place; they snap to edges, each other and a grid", Category.HUD);
	}

	@Override
	protected boolean remembersEnabled() {
		return false;
	}

	@Override
	protected void onEnable() {
		this.screen = new HudEditorScreen(this.client(), () -> this.client().setEnabled(this, false));
		this.client().platform().screens().open(this.screen);
	}

	@Override
	protected void onDisable() {
		if (this.screen != null) {
			this.screen = null;
			this.client().platform().screens().close();
		}
	}

	public @Nullable HudEditorScreen screen() {
		return this.screen;
	}
}
