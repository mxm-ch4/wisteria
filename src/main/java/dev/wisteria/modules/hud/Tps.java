package dev.wisteria.modules.hud;

import dev.wisteria.event.Events;
import dev.wisteria.hud.Anchor;
import dev.wisteria.hud.HudContext;
import dev.wisteria.hud.HudElement;
import java.util.Locale;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;

/** The server's ticks per second; says when the server has gone quiet for a few seconds. */
public final class Tps extends HudElement {
	private static final double QUIET_SECONDS = 3.0;

	private final TpsMeter meter = new TpsMeter();
	private String value = "";
	private String unit = "";
	private int level;

	public Tps() {
		super("TPS", "The server's ticks per second", Anchor.TOP_LEFT, 6.0F, 90.0F);
		this.on(Events.PacketReceive.class, event -> {
			if (event.packet() instanceof ClientboundSetTimePacket time) {
				this.meter.record(time.gameTime(), System.nanoTime());
			}
		});
		this.on(Events.Join.class, event -> this.meter.reset());
	}

	/** The current estimate, NaN until two updates; for diagnostics. */
	public double tps() {
		return this.meter.tps();
	}

	@Override
	protected void onEnable() {
		this.meter.reset();
	}

	/** {value, unit, level}: level 2 for 19.5 and over, 1 for 15 and over, 0 below or when silent; pure. */
	public static String[] describe(double tps, double silence) {
		if (!Double.isNaN(silence) && silence >= QUIET_SECONDS) {
			return new String[] {String.format(Locale.ROOT, "%.0fs", silence), "no ticks", "0"};
		}

		if (Double.isNaN(tps)) {
			return new String[] {"--", "tps", "1"};
		}

		return new String[] {String.format(Locale.ROOT, "%.1f", tps), "tps", tps >= 19.5 ? "2" : tps >= 15.0 ? "1" : "0"};
	}

	@Override
	protected float[] measure(HudContext c) {
		if (this.player() == null && !c.preview()) {
			return null;
		}

		String[] state = this.player() != null ? describe(this.meter.tps(), this.meter.silence(System.nanoTime())) : describe(20.0, Double.NaN);
		this.value = state[0];
		this.unit = state[1];
		this.level = Integer.parseInt(state[2]);
		return Chip.size(c, this.value, this.unit);
	}

	@Override
	protected void draw(HudContext c, float width, float height) {
		Chip.draw(c, width, height, this.value, this.unit, Fps.dot(c.theme(), this.level));
	}
}
