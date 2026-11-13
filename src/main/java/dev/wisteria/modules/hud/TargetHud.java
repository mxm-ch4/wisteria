package dev.wisteria.modules.hud;

import dev.wisteria.hud.Anchor;
import dev.wisteria.hud.HudContext;
import dev.wisteria.hud.HudElement;
import dev.wisteria.setting.IntSetting;
import dev.wisteria.ui.Colors;
import dev.wisteria.ui.Metrics.Type;
import java.util.Locale;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/** The living thing under the crosshair: name, health and distance. Stays a moment after you look away. */
public final class TargetHud extends HudElement {
	private static final float WIDTH = 132.0F;

	private final IntSetting linger = this.add(new IntSetting("Linger", "Seconds it stays after you look away", 3, 0, 10));
	private @Nullable LivingEntity target;
	private long lastSeen;
	private String name = "";
	private float health;
	private float maxHealth;
	private double distance;

	public TargetHud() {
		super("Target", "Health of what you are looking at", Anchor.CENTER, 0.0F, 48.0F);
	}

	/** Health colour from full (success) through half (warning) to empty (danger); pure, for the unit test. */
	public static float fraction(float health, float max) {
		return max <= 0.0F ? 0.0F : Math.max(0.0F, Math.min(1.0F, health / max));
	}

	@Override
	protected float[] measure(HudContext c) {
		LocalPlayer player = this.player();
		long now = System.nanoTime();
		if (player != null) {
			Entity looked = this.mc().crosshairPickEntity;
			if (looked instanceof LivingEntity living && living.isAlive()) {
				this.target = living;
				this.lastSeen = now;
			} else if (this.target != null && (!this.target.isAlive() || now - this.lastSeen > this.linger.get() * 1_000_000_000L)) {
				this.target = null;
			}
		}

		if (this.target != null && player != null) {
			this.name = this.target.getDisplayName().getString();
			this.health = this.target.getHealth();
			this.maxHealth = this.target.getMaxHealth();
			this.distance = player.distanceTo(this.target);
		} else if (c.preview()) {
			this.name = "Zombie";
			this.health = 14.0F;
			this.maxHealth = 20.0F;
			this.distance = 3.2;
		} else {
			return null;
		}

		return new float[] {WIDTH, HudContext.PAD * 2.0F + c.line(Type.LABEL) + 4.0F + 4.0F + c.line(Type.CAPTION)};
	}

	@Override
	protected void draw(HudContext c, float width, float height) {
		c.panel(0.0F, 0.0F, width, height);
		float x = HudContext.PAD;
		float y = HudContext.PAD;
		String distanceText = String.format(Locale.ROOT, "%.1f m", this.distance);
		c.text(Type.LABEL, this.name, x, y, c.theme().text());
		c.text(Type.CAPTION, distanceText, width - HudContext.PAD - c.width(Type.CAPTION, distanceText), y + 1.5F, c.theme().textMuted());
		y += c.line(Type.LABEL) + 4.0F;
		float f = fraction(this.health, this.maxHealth);
		int colour = f > 0.5F ? Colors.mix(c.theme().warning(), c.theme().success(), (f - 0.5F) * 2.0F) : Colors.mix(c.theme().danger(), c.theme().warning(), f * 2.0F);
		c.bar(x, y, width - HudContext.PAD * 2.0F, 4.0F, f, colour);
		y += 4.0F + 1.0F;
		String hp = String.format(Locale.ROOT, "%.1f / %.0f", this.health, this.maxHealth);
		c.text(Type.CAPTION, hp, x, y, c.theme().textMuted());
	}
}
