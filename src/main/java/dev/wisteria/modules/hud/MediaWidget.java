package dev.wisteria.modules.hud;

import dev.wisteria.event.Events;
import dev.wisteria.hud.Anchor;
import dev.wisteria.hud.HudContext;
import dev.wisteria.hud.HudElement;
import dev.wisteria.media.MediaSource;
import dev.wisteria.media.MediaState;
import dev.wisteria.platform.InputBackend;
import dev.wisteria.setting.ActionSetting;
import dev.wisteria.setting.BoolSetting;
import dev.wisteria.setting.InfoSetting;
import dev.wisteria.setting.IntSetting;
import dev.wisteria.setting.KeySetting;
import dev.wisteria.ui.Metrics.Type;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * What is playing on the computer (Windows media controls): title, artist, progress, and play/pause, next, previous
 * from the menu or their own keys. Local only: the helper reads the system's media session; no cover art, no network.
 */
public final class MediaWidget extends HudElement {
	private static final float ICON = 8.0F;

	private final IntSetting maxWidth = this.add(new IntSetting("Max Width", "Longer titles are cut with an ellipsis", 170, 100, 320));
	private final BoolSetting showArtist = this.add(new BoolSetting("Show Artist", "A second line with the artist", true));
	private final BoolSetting showProgress = this.add(new BoolSetting("Show Progress", "The progress bar and times", true));
	private final BoolSetting hidePaused = this.add(new BoolSetting("Hide When Paused", "Show only while something plays", false));
	private final KeySetting playPauseKey = this.add(new KeySetting("Play Pause Key", "Key that plays or pauses"));
	private final KeySetting nextKey = this.add(new KeySetting("Next Key", "Key that skips to the next track"));
	private final KeySetting previousKey = this.add(new KeySetting("Previous Key", "Key that goes back a track"));

	private @Nullable MediaState shown;
	private String title = "";
	private String artist = "";

	public MediaWidget() {
		super("Media", "What is playing on your computer, with play/pause, next and previous", Anchor.TOP, 0.0F, 6.0F);
		this.add(new ActionSetting("Play Pause", "Play or pause what is playing", "Play/Pause", () -> this.send(MediaSource.Command.PLAY_PAUSE)));
		this.add(new ActionSetting("Next", "Next track", "Next", () -> this.send(MediaSource.Command.NEXT)));
		this.add(new ActionSetting("Previous", "Previous track", "Previous", () -> this.send(MediaSource.Command.PREVIOUS)));
		this.add(new InfoSetting("Source", "Where the widget gets what is playing", () -> this.client().media().status()));
		this.on(Events.KeyInput.class, this::key);
	}

	private void send(MediaSource.Command command) {
		this.client().media().send(command);
	}

	private void key(Events.KeyInput event) {
		if (event.action() != InputBackend.Action.PRESS || event.inScreen()) {
			return;
		}

		if (this.playPauseKey.matches(event.key())) {
			this.send(MediaSource.Command.PLAY_PAUSE);
		} else if (this.nextKey.matches(event.key())) {
			this.send(MediaSource.Command.NEXT);
		} else if (this.previousKey.matches(event.key())) {
			this.send(MediaSource.Command.PREVIOUS);
		}
	}

	@Override
	protected void onEnable() {
		this.client().media().start();
	}

	@Override
	protected void onDisable() {
		this.client().media().stop();
	}

	/** The text cut to fit {@code max} pixels, with an ellipsis; pure over a width function, for the unit test. */
	public static String fit(String text, float max, java.util.function.ToDoubleFunction<String> width) {
		if (width.applyAsDouble(text) <= max) {
			return text;
		}

		String cut = text;
		while (!cut.isEmpty() && width.applyAsDouble(cut.strip() + "…") > max) {
			cut = cut.substring(0, cut.length() - 1);
		}

		return cut.strip() + "…";
	}

	@Override
	protected float @Nullable [] measure(HudContext c) {
		Optional<MediaState> now = this.isEnabled() ? this.client().media().current() : Optional.empty();
		MediaState state = now.orElse(null);
		if (state == null && c.preview()) {
			state = new MediaState("preview", "Song title", "Artist", 83_000L, 0L, 215_000L, true, System.currentTimeMillis());
		}

		if (state == null || state.title().isBlank() || this.hidePaused.get() && !state.playing() && !c.preview()) {
			this.shown = null;
			return null;
		}

		this.shown = state;
		float text = this.maxWidth.get() - HudContext.PAD * 2.0F - ICON - 6.0F;
		this.title = fit(state.title(), text, s -> c.width(Type.LABEL, s));
		this.artist = fit(state.artist(), text, s -> c.width(Type.CAPTION, s));
		float width = Math.max(c.width(Type.LABEL, this.title), this.showArtist.get() ? c.width(Type.CAPTION, this.artist) : 0.0F);
		width = Math.max(width + ICON + 6.0F + HudContext.PAD * 2.0F, this.showProgress.get() ? 120.0F : 0.0F);
		float height = HudContext.PAD * 2.0F + c.line(Type.LABEL);
		if (this.showArtist.get() && !this.artist.isEmpty()) {
			height += c.line(Type.CAPTION);
		}

		if (this.showProgress.get() && state.durationMillis() > 0L) {
			height += 6.0F + c.line(Type.MONO);
		}

		return new float[] {Math.min(width, this.maxWidth.get()), height};
	}

	@Override
	protected void draw(HudContext c, float width, float height) {
		MediaState state = this.shown;
		if (state == null) {
			return;
		}

		c.panel(0.0F, 0.0F, width, height);
		float x = HudContext.PAD;
		float y = HudContext.PAD;
		int accent = c.theme().accent();
		float line = c.line(Type.LABEL);
		float iconY = y + (line - ICON) / 2.0F;
		if (state.playing()) {
			// Play: a triangle built from thin bars.
			for (int i = 0; i < 4; i++) {
				float inset = i * 1.0F;
				c.renderer().rect(x + i * 2.0F, iconY + inset, 2.0F, ICON - inset * 2.0F, c.color(accent));
			}
		} else {
			c.renderer().roundedRect(x, iconY, 2.5F, ICON, 1.0F, c.color(accent));
			c.renderer().roundedRect(x + 5.0F, iconY, 2.5F, ICON, 1.0F, c.color(accent));
		}

		float textX = x + ICON + 6.0F;
		c.text(Type.LABEL, this.title, textX, y, c.theme().text());
		y += line;
		if (this.showArtist.get() && !this.artist.isEmpty()) {
			c.text(Type.CAPTION, this.artist, textX, y, c.theme().textMuted());
			y += c.line(Type.CAPTION);
		}

		if (this.showProgress.get() && state.durationMillis() > 0L) {
			long now = System.currentTimeMillis();
			y += 3.0F;
			c.bar(x, y, width - HudContext.PAD * 2.0F, 3.0F, state.progressAt(now), accent);
			y += 3.0F + 1.0F;
			String elapsed = MediaState.time(state.positionAt(now));
			String total = MediaState.time(state.durationMillis());
			c.text(Type.MONO, elapsed, x, y, c.theme().textMuted());
			c.text(Type.MONO, total, width - HudContext.PAD - c.width(Type.MONO, total), y, c.theme().textMuted());
		}
	}
}
