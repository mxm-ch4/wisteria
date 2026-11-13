package dev.wisteria.ui;

import dev.wisteria.Client;
import dev.wisteria.Wisteria;
import dev.wisteria.platform.Key;
import dev.wisteria.render.Renderer2D;
import dev.wisteria.platform.ScreenBackend;
import dev.wisteria.ui.Metrics.Type;

/**
 * Shown once, over the title screen, when the game has finished loading: the mark blooms blossom by
 * blossom, then the screen fades into the menu. Any key or click skips it.
 */
public final class SplashScreen implements ScreenBackend.ClientScreen {
	private static final long HOLD_NANOS = 1_700_000_000L;
	private static final long FADE_NANOS = 350_000_000L;
	private static final float STEP = 15.0F;

	private final Client client;
	private long startedAt;
	private boolean closing;

	public SplashScreen(Client client) {
		this.client = client;
	}

	@Override
	public String title() {
		return Wisteria.NAME;
	}

	@Override
	public boolean blurBackground() {
		return false;
	}

	@Override
	public void render(Renderer2D renderer, float mouseX, float mouseY, float tickDelta) {
		long now = System.nanoTime();
		if (this.startedAt == 0L) {
			// Counted from the first frame actually drawn, not from when the screen was requested.
			this.startedAt = now;
		}

		long elapsed = now - this.startedAt;
		float fade = 1.0F - Math.max(0.0F, Math.min(1.0F, (float)(elapsed - HOLD_NANOS) / FADE_NANOS));
		if (elapsed > HOLD_NANOS + FADE_NANOS && !this.closing) {
			this.closing = true;
			this.client.platform().screens().close();
		}

		Theme theme = this.client.themes().current();
		float width = renderer.width();
		float height = renderer.height();
		renderer.rect(0.0F, 0.0F, width, height, Colors.fade(theme.background(), fade));

		float centerX = width / 2.0F;
		float top = height / 2.0F - 62.0F;
		float seconds = elapsed / 1.0e9F;
		renderer.shadow(centerX - 26.0F, top + 14.0F, 52.0F, 62.0F, 26.0F, 34.0F, Colors.fade(Colors.withAlpha(theme.accent(), 70), fade));
		float[] grow = new float[BrandMark.CLUSTER.length];
		for (int i = 0; i < grow.length; i++) {
			// Each blossom pops in a moment after the one above it.
			grow[i] = Easing.OUT_BACK.apply(Math.max(0.0F, Math.min(1.0F, (seconds - 0.05F * i) / 0.35F)));
		}

		BrandMark.draw(renderer, centerX - BrandMark.width(STEP) / 2.0F, top - STEP * 0.45F, STEP, theme, fade, grow);

		float text = Easing.OUT_CUBIC.apply(Math.max(0.0F, Math.min(1.0F, (seconds - 0.55F) / 0.4F))) * fade;
		if (text > 0.03F) {
			String name = Wisteria.NAME.toUpperCase(java.util.Locale.ROOT);
			float tracking = 4.0F;
			float nameWidth = -tracking;
			for (int i = 0; i < name.length(); i++) {
				nameWidth += Type.TITLE.width(renderer, name.substring(i, i + 1)) + tracking;
			}

			float x = centerX - nameWidth / 2.0F;
			float y = top + 96.0F + (1.0F - text) * 4.0F;
			for (int i = 0; i < name.length(); i++) {
				String letter = name.substring(i, i + 1);
				Type.TITLE.draw(renderer, letter, x, y, Colors.fade(theme.text(), text));
				x += Type.TITLE.width(renderer, letter) + tracking;
			}

			String version = "minecraft " + this.client.platform().minecraftVersion();
			Type.MONO.draw(renderer, version, centerX - Type.MONO.width(renderer, version) / 2.0F, y + 20.0F, Colors.fade(theme.textMuted(), text));
		}
	}

	private boolean skip() {
		if (!this.closing) {
			this.closing = true;
			this.client.platform().screens().close();
		}

		return true;
	}

	@Override
	public boolean mouseDown(float x, float y, ScreenBackend.MouseButton button) {
		return this.skip();
	}

	@Override
	public boolean keyDown(Key key, boolean shift, boolean control, boolean alt) {
		return this.skip();
	}
}
