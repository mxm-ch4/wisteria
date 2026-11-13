package dev.wisteria.modules.hud;

import dev.wisteria.hud.Anchor;
import dev.wisteria.hud.HudContext;
import dev.wisteria.hud.HudElement;
import dev.wisteria.setting.BoolSetting;
import dev.wisteria.ui.Metrics.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.player.LocalPlayer;

/** Where you are, and where that is in the other dimension (Overworld and Nether, 8 to 1). */
public final class Coordinates extends HudElement {
	private final BoolSetting other = this.add(new BoolSetting("Other Dimension", "Also show Nether or Overworld coordinates", true));
	private final BoolSetting decimals = this.add(new BoolSetting("Decimals", "One decimal place", false));
	private List<String[]> rows = List.of();

	public Coordinates() {
		super("Coordinates", "Your position, and the matching one in the other dimension", Anchor.BOTTOM_LEFT, 6.0F, -6.0F);
	}

	/** Rows of {label, value}; pure, for the unit test. */
	public static List<String[]> rows(double x, double y, double z, String dimension, boolean other, boolean decimals) {
		String format = decimals ? "%.1f" : "%.0f";
		List<String[]> rows = new ArrayList<>();
		rows.add(new String[] {"XYZ", xyz(format, x, y, z)});
		if (other && dimension.equals("minecraft:overworld")) {
			rows.add(new String[] {"Nether", xyz(format, x / 8.0, y, z / 8.0)});
		} else if (other && dimension.equals("minecraft:the_nether")) {
			rows.add(new String[] {"Overworld", xyz(format, x * 8.0, y, z * 8.0)});
		}

		return rows;
	}

	private static String xyz(String format, double x, double y, double z) {
		// Whole blocks are floored, the way the debug screen counts them.
		if (format.equals("%.0f")) {
			return (long)Math.floor(x) + " " + (long)Math.floor(y) + " " + (long)Math.floor(z);
		}

		return String.format(Locale.ROOT, format + " " + format + " " + format, x, y, z);
	}

	@Override
	protected float[] measure(HudContext c) {
		LocalPlayer player = this.player();
		if (player != null) {
			this.rows = rows(player.getX(), player.getY(), player.getZ(), player.level().dimension().identifier().toString(), this.other.get(), this.decimals.get());
		} else if (c.preview()) {
			this.rows = rows(120.5, 64.0, -35.2, "minecraft:overworld", this.other.get(), this.decimals.get());
		} else {
			return null;
		}

		float labels = 0.0F;
		float values = 0.0F;
		for (String[] row : this.rows) {
			labels = Math.max(labels, c.width(Type.CAPTION, row[0]));
			values = Math.max(values, c.width(Type.MONO, row[1]));
		}

		return new float[] {HudContext.PAD * 2.0F + labels + 6.0F + values, HudContext.PAD * 2.0F + this.rows.size() * c.line(Type.MONO)};
	}

	@Override
	protected void draw(HudContext c, float width, float height) {
		c.panel(0.0F, 0.0F, width, height);
		float labels = 0.0F;
		for (String[] row : this.rows) {
			labels = Math.max(labels, c.width(Type.CAPTION, row[0]));
		}

		float y = HudContext.PAD;
		for (int i = 0; i < this.rows.size(); i++) {
			String[] row = this.rows.get(i);
			float line = c.line(Type.MONO);
			c.text(Type.CAPTION, row[0], HudContext.PAD, y + (line - c.line(Type.CAPTION)) / 2.0F, i == 0 ? c.theme().accent() : c.theme().textMuted());
			c.text(Type.MONO, row[1], HudContext.PAD + labels + 6.0F, y, i == 0 ? c.theme().text() : c.theme().textMuted());
			y += line;
		}
	}
}
