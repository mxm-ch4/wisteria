package dev.wisteria.modules.hud;

import dev.wisteria.hud.Anchor;
import dev.wisteria.hud.HudContext;
import dev.wisteria.hud.HudElement;
import dev.wisteria.module.Module;
import dev.wisteria.ui.Metrics.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Modules with a key bound to them, the key, and whether each is on. */
public final class Keybinds extends HudElement {
	private record Row(String name, String key, boolean on) {
	}

	private final List<Row> rows = new ArrayList<>();

	public Keybinds() {
		super("Keybinds", "Bound keys and what they toggle", Anchor.LEFT, 6.0F, 30.0F);
	}

	/** "right_shift" to "RShift", "g" to "G"; pure, for the unit test. */
	public static String keyLabel(String key) {
		String upper = key.toUpperCase(Locale.ROOT);
		if (upper.startsWith("RIGHT_")) {
			return "R" + capitalise(upper.substring(6));
		}

		if (upper.startsWith("LEFT_")) {
			return "L" + capitalise(upper.substring(5));
		}

		return upper.length() <= 3 ? upper : capitalise(upper).replace('_', ' ');
	}

	private static String capitalise(String upper) {
		return upper.charAt(0) + upper.substring(1).toLowerCase(Locale.ROOT);
	}

	@Override
	protected float[] measure(HudContext c) {
		this.rows.clear();
		for (Module module : this.client().modules().all()) {
			if (module.bind().get().isPresent()) {
				this.rows.add(new Row(module.name(), keyLabel(module.bind().format()), module.isEnabled()));
			}
		}

		if (this.rows.isEmpty() && c.preview()) {
			this.rows.add(new Row("ClickGUI", "RShift", false));
		}

		if (this.rows.isEmpty()) {
			return null;
		}

		float names = c.width(Type.CAPTION, "KEYBINDS");
		float keys = 0.0F;
		for (Row row : this.rows) {
			names = Math.max(names, c.width(Type.BODY, row.name()));
			keys = Math.max(keys, c.width(Type.MONO, row.key()) + 6.0F);
		}

		return new float[] {HudContext.PAD * 2.0F + names + 10.0F + keys, HudContext.PAD * 2.0F + c.line(Type.CAPTION) + 2.0F + this.rows.size() * (c.line(Type.BODY) + 1.0F)};
	}

	@Override
	protected void draw(HudContext c, float width, float height) {
		c.panel(0.0F, 0.0F, width, height);
		c.text(Type.CAPTION, "KEYBINDS", HudContext.PAD, HudContext.PAD, c.theme().textMuted());
		float y = HudContext.PAD + c.line(Type.CAPTION) + 2.0F;
		for (Row row : this.rows) {
			float line = c.line(Type.BODY);
			c.text(Type.BODY, row.name(), HudContext.PAD, y, row.on() ? c.theme().accent() : c.theme().text());
			float keyWidth = c.width(Type.MONO, row.key()) + 6.0F;
			float keyX = width - HudContext.PAD - keyWidth;
			c.renderer().roundedRect(keyX, y + 0.5F, keyWidth, line - 1.0F, 3.0F, c.color(c.theme().raised()));
			c.text(Type.MONO, row.key(), keyX + 3.0F, y + (line - c.line(Type.MONO)) / 2.0F, c.theme().textMuted());
			y += line + 1.0F;
		}
	}
}
