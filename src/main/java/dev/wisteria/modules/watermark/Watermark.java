package dev.wisteria.modules.watermark;

import dev.wisteria.Wisteria;
import dev.wisteria.hud.Anchor;
import dev.wisteria.hud.HudContext;
import dev.wisteria.hud.HudElement;
import dev.wisteria.setting.BoolSetting;
import dev.wisteria.setting.StringSetting;
import dev.wisteria.ui.BrandMark;
import dev.wisteria.ui.Metrics.Type;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;

/** The client's mark and name, with frames per second and ping. */
public final class Watermark extends HudElement {
	private static final float MARK_STEP = 3.2F;

	private final StringSetting text = this.add(new StringSetting("Text", "What to show", Wisteria.NAME, 48));
	private final BoolSetting showFps = this.add(new BoolSetting("Show FPS", "Frames per second", true));
	private final BoolSetting showPing = this.add(new BoolSetting("Show Ping", "Round trip to the server in milliseconds", true));
	private final BoolSetting showVersion = this.add(new BoolSetting("Show Version", "Append the Minecraft version", false));
	private String details = "";

	public Watermark() {
		super("Watermark", "The client's mark, name, FPS and ping", Anchor.TOP_LEFT, 6.0F, 6.0F);
	}

	/** "143 fps · 23 ms"; pure, for the unit test. Parts that are off or unknown (ping -1) are left out. */
	public static String details(int fps, int ping, boolean showFps, boolean showPing, String version) {
		List<String> parts = new ArrayList<>();
		if (showFps) {
			parts.add(fps + " fps");
		}

		if (showPing && ping >= 0) {
			parts.add(ping + " ms");
		}

		if (!version.isEmpty()) {
			parts.add(version);
		}

		return String.join(" · ", parts);
	}

	@Override
	protected float[] measure(HudContext c) {
		int fps = 0;
		int ping = -1;
		LocalPlayer player = this.player();
		if (player != null) {
			fps = this.mc().getFps();
			PlayerInfo info = player.connection.getPlayerInfo(player.getUUID());
			ping = info != null ? info.getLatency() : -1;
		} else if (c.preview()) {
			fps = 144;
			ping = 23;
		}

		this.details = details(fps, ping, this.showFps.get(), this.showPing.get(),
			this.showVersion.get() ? this.client().platform().minecraftVersion() : "");
		float textWidth = Math.max(c.width(Type.LABEL, this.text.get()), this.details.isEmpty() ? 0.0F : c.width(Type.MONO, this.details));
		float textHeight = c.line(Type.LABEL) + (this.details.isEmpty() ? 0.0F : c.line(Type.MONO));
		float height = Math.max(BrandMark.height(MARK_STEP), textHeight) + HudContext.PAD * 2.0F;
		return new float[] {HudContext.PAD * 2.0F + BrandMark.width(MARK_STEP) + 6.0F + textWidth + HudContext.PAD, height};
	}

	@Override
	protected void draw(HudContext c, float width, float height) {
		c.panel(0.0F, 0.0F, width, height);
		BrandMark.draw(c.renderer(), HudContext.PAD + 1.0F, (height - BrandMark.height(MARK_STEP)) / 2.0F, MARK_STEP, c.theme(), c.alpha(), null);
		float x = HudContext.PAD * 2.0F + BrandMark.width(MARK_STEP) + 4.0F;
		float textHeight = c.line(Type.LABEL) + (this.details.isEmpty() ? 0.0F : c.line(Type.MONO));
		float y = (height - textHeight) / 2.0F;
		c.text(Type.LABEL, this.text.get(), x, y, c.theme().text());
		if (!this.details.isEmpty()) {
			c.text(Type.MONO, this.details, x, y + c.line(Type.LABEL), c.theme().textMuted());
		}
	}
}
