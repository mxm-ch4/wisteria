package dev.wisteria.modules.hud;

import dev.wisteria.hud.Anchor;
import dev.wisteria.hud.HudContext;
import dev.wisteria.hud.HudElement;
import dev.wisteria.ui.Colors;
import dev.wisteria.ui.Metrics.Type;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/** Active effects: colour, name and level, time left; blinks in the last ten seconds. */
public final class Potions extends HudElement {
	private record Row(String name, String time, int colour, boolean ending) {
	}

	private final List<Row> rows = new ArrayList<>();

	public Potions() {
		super("Potions", "Active effects and the time they have left", Anchor.RIGHT, -6.0F, 0.0F);
	}

	/** "1:05" for 1300 ticks, "∞" for infinite; pure, for the unit test. */
	public static String time(int ticks, boolean infinite) {
		if (infinite) {
			return "∞";
		}

		int seconds = Math.max(0, ticks) / 20;
		return seconds / 60 + ":" + String.format("%02d", seconds % 60);
	}

	/** Level 1 has no number; 2 to 10 are Roman; higher stay Arabic. */
	public static String level(int amplifier) {
		String[] roman = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};
		return amplifier >= 0 && amplifier < roman.length ? roman[amplifier] : " " + (amplifier + 1);
	}

	@Override
	protected float[] measure(HudContext c) {
		this.rows.clear();
		LocalPlayer player = this.player();
		if (player != null) {
			for (MobEffectInstance effect : player.getActiveEffects()) {
				MobEffect type = effect.getEffect().value();
				this.rows.add(new Row(type.getDisplayName().getString() + level(effect.getAmplifier()),
					time(effect.getDuration(), effect.isInfiniteDuration()), 0xFF000000 | type.getColor(),
					!effect.isInfiniteDuration() && effect.getDuration() < 200));
			}
		}

		if (this.rows.isEmpty() && c.preview()) {
			this.rows.add(new Row("Speed II", "1:05", 0xFF33EBFF, false));
			this.rows.add(new Row("Night Vision", "7:42", 0xFFC2FF66, false));
		}

		if (this.rows.isEmpty()) {
			return null;
		}

		float names = 0.0F;
		float times = 0.0F;
		for (Row row : this.rows) {
			names = Math.max(names, c.width(Type.BODY, row.name()));
			times = Math.max(times, c.width(Type.MONO, row.time()));
		}

		return new float[] {HudContext.PAD * 2.0F + 9.0F + names + 10.0F + times, HudContext.PAD * 2.0F + this.rows.size() * c.line(Type.BODY)};
	}

	@Override
	protected void draw(HudContext c, float width, float height) {
		c.panel(0.0F, 0.0F, width, height);
		float y = HudContext.PAD;
		boolean blinkOff = (System.currentTimeMillis() / 400L) % 2L == 0L;
		for (Row row : this.rows) {
			float line = c.line(Type.BODY);
			c.renderer().roundedRect(HudContext.PAD, y + line / 2.0F - 2.5F, 5.0F, 5.0F, 2.5F, c.color(row.colour()));
			c.text(Type.BODY, row.name(), HudContext.PAD + 9.0F, y, c.theme().text());
			int timeColour = row.ending() ? (blinkOff ? c.theme().warning() : Colors.fade(c.theme().warning(), 0.45F)) : c.theme().textMuted();
			c.text(Type.MONO, row.time(), width - HudContext.PAD - c.width(Type.MONO, row.time()), y + (line - c.line(Type.MONO)) / 2.0F, timeColour);
			y += line;
		}
	}
}
