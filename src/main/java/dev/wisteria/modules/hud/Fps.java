package dev.wisteria.modules.hud;

import dev.wisteria.hud.Anchor;
import dev.wisteria.hud.HudContext;
import dev.wisteria.hud.HudElement;
import dev.wisteria.ui.Theme;

/** Frames per second, with a dot that turns amber under 60 and red under 30. */
public final class Fps extends HudElement {
	private int fps;

	public Fps() {
		super("FPS", "Frames per second", Anchor.TOP_LEFT, 6.0F, 68.0F);
	}

	/** 2 for smooth, 1 for playable, 0 for choppy; pure, for the unit test. */
	public static int level(int fps) {
		return fps >= 60 ? 2 : fps >= 30 ? 1 : 0;
	}

	static int dot(Theme theme, int level) {
		return level == 2 ? theme.success() : level == 1 ? theme.warning() : theme.danger();
	}

	@Override
	protected float[] measure(HudContext c) {
		if (this.player() == null && !c.preview()) {
			return null;
		}

		this.fps = this.player() != null ? this.mc().getFps() : 144;
		return Chip.size(c, Integer.toString(this.fps), "fps");
	}

	@Override
	protected void draw(HudContext c, float width, float height) {
		Chip.draw(c, width, height, Integer.toString(this.fps), "fps", dot(c.theme(), level(this.fps)));
	}
}
