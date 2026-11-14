package dev.wisteria.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wisteria.modules.hud.MediaWidget;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The media widget's logic and the Windows helper's output, without Windows. */
class MediaTest {
	@Test
	void positionMovesOnOnlyWhilePlaying() {
		MediaState playing = new MediaState("app", "t", "a", 10_000L, 500L, 60_000L, true, 1_000L);
		assertEquals(12_500L, playing.positionAt(3_000L));
		MediaState paused = new MediaState("app", "t", "a", 10_000L, 500L, 60_000L, false, 1_000L);
		assertEquals(10_000L, paused.positionAt(30_000L));
		assertEquals(60_000L, playing.positionAt(10_000_000L), "never past the end");
		assertEquals(0.5F, new MediaState("app", "t", "a", 30_000L, 0L, 60_000L, false, 0L).progressAt(5L), 1e-6);
		assertEquals(0.0F, new MediaState("app", "t", "a", 30_000L, 0L, 0L, true, 0L).progressAt(5L), "unknown length");
	}

	@Test
	void timesReadLikeAPlayer() {
		assertEquals("0:00", MediaState.time(0L));
		assertEquals("3:07", MediaState.time(187_400L));
		assertEquals("1:02:45", MediaState.time(3_765_000L));
	}

	@Test
	void longTitlesAreCut() {
		assertEquals("Short", MediaWidget.fit("Short", 100.0F, s -> s.length() * 5.0));
		String cut = MediaWidget.fit("A very long song title indeed", 60.0F, s -> s.length() * 5.0);
		assertTrue(cut.endsWith("…") && cut.length() * 5.0 <= 60.0, cut);
	}

	@Test
	void helperLinesAreRead() {
		MediaState state = SmtcMediaSource.parse("{\"active\":true,\"app\":\"Spotify.exe\",\"title\":\"Song\",\"artist\":\"Band\",\"position\":61000,"
			+ "\"duration\":200000,\"age\":250,\"playing\":true}", 5_000L);
		assertEquals("Song", state.title());
		assertEquals("Band", state.artist());
		assertEquals(61_000L, state.positionMillis());
		assertEquals(250L, state.ageMillis());
		assertTrue(state.playing());
		assertEquals(61_250L, state.positionAt(5_000L));
		assertNull(SmtcMediaSource.parse("{\"active\":false}", 0L), "nothing playing");
		assertNull(SmtcMediaSource.parse("garbage", 0L));
		assertEquals("", SmtcMediaSource.parse("{\"active\":true,\"title\":null}", 0L).title(), "nulls do not break it");
	}

	@Test
	void theHelperAsksForNoCoverAndNoNetwork() {
		String script = SmtcMediaSource.SCRIPT.toLowerCase(java.util.Locale.ROOT);
		assertFalse(script.contains("thumbnail"));
		assertFalse(script.contains("http"));
		assertFalse(script.contains("invoke-webrequest"));
	}

	@Test
	void notWindowsSaysSo() {
		if (SmtcMediaSource.available()) {
			return;
		}

		SmtcMediaSource source = new SmtcMediaSource();
		source.start();
		assertTrue(source.status().contains("Windows"), source.status());
		assertTrue(source.current().isEmpty());
		source.send(MediaSource.Command.NEXT);
		source.stop();
	}

	@Test
	void theFakePlaysLikeAPlayer() {
		FakeMediaSource fake = new FakeMediaSource().add("One", "A", 100).add("Two", "B", 200);
		assertTrue(fake.current().isEmpty(), "not started");
		fake.start();
		assertEquals("One", fake.current().orElseThrow().title());
		fake.send(MediaSource.Command.PLAY_PAUSE);
		assertFalse(fake.current().orElseThrow().playing());
		fake.send(MediaSource.Command.NEXT);
		assertEquals("Two", fake.current().orElseThrow().title());
		fake.send(MediaSource.Command.PREVIOUS);
		assertEquals("One", fake.current().orElseThrow().title());
		assertEquals(List.of(MediaSource.Command.PLAY_PAUSE, MediaSource.Command.NEXT, MediaSource.Command.PREVIOUS), fake.received());
	}
}
