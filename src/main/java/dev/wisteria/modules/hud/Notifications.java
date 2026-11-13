package dev.wisteria.modules.hud;

import dev.wisteria.hud.Anchor;
import dev.wisteria.hud.HudContext;
import dev.wisteria.hud.HudElement;
import dev.wisteria.hud.Toasts;
import dev.wisteria.ui.Colors;
import dev.wisteria.ui.Metrics;
import dev.wisteria.ui.Metrics.Type;
import dev.wisteria.ui.Theme;
import java.util.List;

/** Toasts: a module switched on or off, a death, anything else the client reports. */
public final class Notifications extends HudElement {
	private static final float WIDTH = 150.0F;
	private static final float GAP = 4.0F;

	private List<Toasts.Shown> shown = List.of();

	public Notifications() {
		super("Notifications", "Toasts when modules switch and when something happens", Anchor.BOTTOM_RIGHT, -6.0F, -6.0F);
	}

	static int stripe(Theme theme, Toasts.Kind kind) {
		return switch (kind) {
			case ON -> theme.success();
			case OFF -> theme.textMuted();
			case WARNING -> theme.warning();
			case INFO -> theme.accent();
		};
	}

	private float toastHeight(HudContext c) {
		return HudContext.PAD * 2.0F + c.line(Type.LABEL) + c.line(Type.CAPTION);
	}

	@Override
	protected float[] measure(HudContext c) {
		this.shown = this.client().hud().toasts().shown();
		if (this.shown.isEmpty() && c.preview()) {
			this.shown = List.of(new Toasts.Shown(new Toasts.Toast("Xray", "Enabled", Toasts.Kind.ON, 0L), 1.0F, 0.6F));
		}

		if (this.shown.isEmpty()) {
			return null;
		}

		float height = 0.0F;
		for (Toasts.Shown toast : this.shown) {
			height += (this.toastHeight(c) + GAP) * toast.presence();
		}

		return new float[] {WIDTH, Math.max(1.0F, height - GAP)};
	}

	@Override
	protected void draw(HudContext c, float width, float height) {
		boolean up = c.anchor().bottom();
		boolean right = c.anchor().right() || c.anchor().fx() == 0.5F;
		float toastHeight = this.toastHeight(c);
		float y = up ? height : 0.0F;
		// Newest nearest the edge it is pinned to.
		for (int i = this.shown.size() - 1; i >= 0; i--) {
			Toasts.Shown toast = this.shown.get(i);
			float step = (toastHeight + GAP) * toast.presence();
			if (up) {
				y -= toastHeight;
			}

			float slide = (1.0F - toast.presence()) * (WIDTH + 8.0F) * (right ? 1.0F : -1.0F);
			float opacity = c.alpha() * toast.presence();
			c.renderer().push();
			c.renderer().translate(slide, 0.0F);
			Theme theme = c.theme();
			c.renderer().roundedRect(0.0F, y, width, toastHeight, Metrics.Radius.M, Colors.fade(Colors.withAlpha(theme.surface(), 0xE8), opacity));
			c.renderer().roundedOutline(0.0F, y, width, toastHeight, Metrics.Radius.M, Metrics.Stroke.HAIRLINE, Colors.fade(Colors.withAlpha(theme.outline(), 0x90), opacity));
			c.renderer().roundedRect(3.0F, y + 4.0F, 2.0F, toastHeight - 8.0F, 1.0F, Colors.fade(stripe(theme, toast.toast().kind()), opacity));
			Metrics.Type.LABEL.draw(c.renderer(), toast.toast().title(), HudContext.PAD + 4.0F, y + HudContext.PAD, Colors.fade(theme.text(), opacity));
			Metrics.Type.CAPTION.draw(c.renderer(), toast.toast().text(), HudContext.PAD + 4.0F, y + HudContext.PAD + c.line(Type.LABEL), Colors.fade(theme.textMuted(), opacity));
			// Time left, as a hairline along the bottom.
			c.renderer().rect(Metrics.Radius.M, y + toastHeight - 2.0F, (width - Metrics.Radius.M * 2.0F) * toast.remaining(), 1.0F,
				Colors.fade(Colors.withAlpha(stripe(theme, toast.toast().kind()), 0xA0), opacity));
			c.renderer().pop();
			y = up ? y - (step - toastHeight) : y + step;
		}
	}
}
