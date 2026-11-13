package dev.wisteria.modules.hud;

import dev.wisteria.hud.Anchor;
import dev.wisteria.hud.HudContext;
import dev.wisteria.hud.HudElement;
import dev.wisteria.proxy.ProxyManager;
import dev.wisteria.ui.Metrics.Type;

/** Which way connections go: through which proxy, blocked, or directly (a warning, in the warning colour). */
public final class ProxyHud extends HudElement {
	private String text = "";
	private int dot;

	public ProxyHud() {
		super("Proxy Status", "Shows the proxy connections go through, or warns that they go directly", Anchor.TOP, 0.0F, 6.0F);
	}

	/** {text, level}: level 2 through a proxy, 1 blocked, 0 direct; pure, for the unit test. */
	public static String[] describe(ProxyManager.Route route) {
		return switch (route) {
			case ProxyManager.Route.Via via -> new String[] {"Proxy: " + via.proxy().name() + " (" + via.proxy().address() + ")", "2"};
			case ProxyManager.Route.Blocked blocked -> new String[] {"Proxy: blocked, nothing connects", "1"};
			case ProxyManager.Route.Direct direct -> new String[] {"Proxy off: direct connection", "0"};
		};
	}

	@Override
	protected float[] measure(HudContext c) {
		String[] state = describe(this.client().proxies().route());
		this.text = state[0];
		this.dot = switch (state[1]) {
			case "2" -> c.theme().success();
			case "1" -> c.theme().danger();
			default -> c.theme().warning();
		};
		return new float[] {HudContext.PAD * 2.0F + 9.0F + c.width(Type.BODY, this.text), HudContext.PAD * 2.0F + c.line(Type.BODY)};
	}

	@Override
	protected void draw(HudContext c, float width, float height) {
		c.panel(0.0F, 0.0F, width, height);
		c.renderer().roundedRect(HudContext.PAD, height / 2.0F - 2.5F, 5.0F, 5.0F, 2.5F, c.color(this.dot));
		c.text(Type.BODY, this.text, HudContext.PAD + 9.0F, HudContext.PAD, c.theme().text());
	}
}
