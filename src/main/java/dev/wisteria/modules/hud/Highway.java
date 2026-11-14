package dev.wisteria.modules.hud;

import dev.wisteria.hud.Anchor;
import dev.wisteria.hud.HudContext;
import dev.wisteria.hud.HudElement;
import dev.wisteria.setting.BoolSetting;
import dev.wisteria.setting.DoubleSetting;
import dev.wisteria.setting.StringSetting;
import dev.wisteria.ui.Metrics.Type;
import java.util.Locale;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Nether highway navigator: the nearest highway (axes, diagonals, ring roads), how far off it you are and which way to
 * go, and a needle for the direction of travel against where you look, with "on axis" once you are on it.
 */
public final class Highway extends HudElement {
	private final BoolSetting axes = this.add(new BoolSetting("Axes", "The four axes from 0 0", true));
	private final BoolSetting diagonals = this.add(new BoolSetting("Diagonals", "The four diagonals from 0 0", true));
	private final StringSetting rings = this.add(new StringSetting("Ring Roads", "Square ring roads at these distances, e.g. 1000 5000", "", 64));
	private final DoubleSetting tolerance = this.add(new DoubleSetting("On Width", "How far off still counts as on it, in blocks", 2.0, 0.5, 16.0, 0.5));
	private final BoolSetting netherOnly = this.add(new BoolSetting("Nether Only", "Only in the Nether, where the highways are", true));

	private HighwayLogic.@Nullable Nearest nearest;
	private String guidance = "";
	private float turn;

	public Highway() {
		super("Highway", "The nearest Nether highway: how far off, which way, and a needle for the direction of travel", Anchor.TOP, 0.0F, 70.0F);
	}

	/** What the element shows now; null when there is nothing to show. For tests. */
	public HighwayLogic.@Nullable Nearest current() {
		this.update();
		return this.nearest;
	}

	public String guidance() {
		this.update();
		return this.guidance;
	}

	private void update() {
		LocalPlayer player = this.player();
		this.nearest = null;
		if (player == null || this.level() == null || this.netherOnly.get() && this.level().dimension() != Level.NETHER) {
			return;
		}

		this.nearest = HighwayLogic.nearest(player.getX(), player.getZ(), this.axes.get(), this.diagonals.get(), HighwayLogic.parseRings(this.rings.get()));
		if (this.nearest != null) {
			this.guidance = HighwayLogic.guidance(this.nearest, player.getX(), player.getZ(), this.tolerance.get());
			this.turn = HighwayLogic.turn(player.getYRot(), this.nearest.travelYaw());
		}
	}

	@Override
	protected float @Nullable [] measure(HudContext c) {
		this.update();
		if (this.nearest == null && c.preview()) {
			this.nearest = new HighwayLogic.Nearest("+X axis", 0.4, 0, 0, -90.0F);
			this.guidance = "on it";
			this.turn = 12.0F;
		}

		if (this.nearest == null) {
			return null;
		}

		float text = Math.max(c.width(Type.LABEL, this.nearest.name()), c.width(Type.CAPTION, this.guidance + "  " + this.turnText()));
		return new float[] {HudContext.PAD * 3.0F + 22.0F + text, HudContext.PAD * 2.0F + Math.max(22.0F, c.line(Type.LABEL) + c.line(Type.CAPTION))};
	}

	private String turnText() {
		float abs = Math.abs(this.turn);
		if (abs < 2.0F) {
			return "heading along";
		}

		return String.format(Locale.ROOT, "turn %d° %s", Math.round(abs), this.turn > 0 ? "right" : "left");
	}

	@Override
	protected void draw(HudContext c, float width, float height) {
		HighwayLogic.Nearest shown = this.nearest;
		if (shown == null) {
			return;
		}

		c.panel(0.0F, 0.0F, width, height);
		boolean on = this.guidance.equals("on it");
		int accent = on ? c.theme().success() : c.theme().accent();
		// The needle: up is where you look, it points along the highway's direction of travel.
		float cx = HudContext.PAD + 11.0F;
		float cy = height / 2.0F;
		c.renderer().roundedOutline(cx - 11.0F, cy - 11.0F, 22.0F, 22.0F, 11.0F, 1.0F, c.color(c.theme().outline()));
		c.renderer().push();
		c.renderer().translate(cx, cy);
		c.renderer().rotate((float) Math.toRadians(this.turn));
		c.renderer().roundedRect(-1.5F, -9.0F, 3.0F, 10.0F, 1.5F, c.color(accent));
		c.renderer().roundedRect(-1.0F, 1.0F, 2.0F, 6.0F, 1.0F, c.color(c.theme().textMuted()));
		c.renderer().pop();

		float x = HudContext.PAD * 2.0F + 22.0F;
		float y = (height - c.line(Type.LABEL) - c.line(Type.CAPTION)) / 2.0F;
		c.text(Type.LABEL, shown.name(), x, y, on ? c.theme().success() : c.theme().text());
		c.text(Type.CAPTION, this.guidance + "  " + this.turnText(), x, y + c.line(Type.LABEL), c.theme().textMuted());
	}
}
