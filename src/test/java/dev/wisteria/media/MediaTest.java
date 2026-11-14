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
	void smtcValuesBecomeTheWidgetState() {
		// 3:00 track that started at tick 0, at 1:01.5, reported 250 ms ago, playing (status 4).
		long now = 1_800_000_000_000L;
		long reported = (now - 250L) * 10_000L + WinRt.UNIX_EPOCH_FILETIME;
		MediaState state = WinRtMediaSource.state("Spotify.exe", "Song", "Band", 0L, 1_800_000_000L, 615_000_000L, reported, 4, now);
		assertEquals("Song", state.title());
		assertEquals("Band", state.artist());
		assertEquals(180_000L, state.durationMillis());
		assertEquals(61_500L, state.positionMillis());
		assertEquals(250L, state.ageMillis());
		assertTrue(state.playing());
		assertFalse(WinRtMediaSource.state("a", "Song", "", 0L, 0L, 0L, 0L, 5, now).playing(), "paused");
		assertNull(WinRtMediaSource.state("a", "", " ", 0L, 0L, 0L, 0L, 4, now), "a session with no track shows nothing");
	}

	@Test
	void notWindowsSaysSo() {
		if (WinRtMediaSource.available()) {
			return;
		}

		WinRtMediaSource source = new WinRtMediaSource();
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
