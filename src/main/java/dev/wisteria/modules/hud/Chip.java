package dev.wisteria.modules.hud;

import dev.wisteria.hud.HudContext;
import dev.wisteria.ui.Metrics.Type;

/** The small "dot, value, unit" panel the FPS and TPS elements share. */
final class Chip {
	private Chip() {
	}

	static float[] size(HudContext c, String value, String unit) {
		return new float[] {HudContext.PAD * 2.0F + 9.0F + c.width(Type.MONO, value) + 3.0F + c.width(Type.CAPTION, unit), c.line(Type.MONO) + HudContext.PAD * 2.0F};
	}

	static void draw(HudContext c, float width, float height, String value, String unit, int dot) {
		c.panel(0.0F, 0.0F, width, height);
		c.renderer().roundedRect(HudContext.PAD, height / 2.0F - 2.5F, 5.0F, 5.0F, 2.5F, c.color(dot));
		float x = HudContext.PAD + 9.0F;
		c.text(Type.MONO, value, x, HudContext.PAD, c.theme().text());
		c.text(Type.CAPTION, unit, x + c.width(Type.MONO, value) + 3.0F, HudContext.PAD + (c.line(Type.MONO) - c.line(Type.CAPTION)) / 2.0F, c.theme().textMuted());
	}
}
