package dev.wisteria.media;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A media source that plays what it is told: for tests, and for the gate on machines without Windows. Commands act on
 * it the way a player would (pause stops the clock, next moves to the next track of its list).
 */
public final class FakeMediaSource implements MediaSource {
	private final List<String[]> tracks = new ArrayList<>();
	private final List<Command> received = new ArrayList<>();
	private int track;
	private long position;
	private boolean playing = true;
	private long since = System.currentTimeMillis();
	private boolean started;

	/** @param tracks title, artist, length in seconds */
	public FakeMediaSource add(String title, String artist, int seconds) {
		this.tracks.add(new String[] {title, artist, Integer.toString(seconds)});
		return this;
	}

	@Override
	public synchronized void start() {
		this.started = true;
	}

	@Override
	public synchronized void stop() {
		this.started = false;
	}

	public synchronized boolean started() {
		return this.started;
	}

	/** Every command it got, in order. */
	public synchronized List<Command> received() {
		return List.copyOf(this.received);
	}

	public synchronized void seek(long millis) {
		this.position = millis;
		this.since = System.currentTimeMillis();
	}

	@Override
	public synchronized Optional<MediaState> current() {
		if (!this.started || this.tracks.isEmpty()) {
			return Optional.empty();
		}

		String[] now = this.tracks.get(this.track);
		return Optional.of(new MediaState("fake", now[0], now[1], this.position, 0L, Long.parseLong(now[2]) * 1000L, this.playing, this.since));
	}

	@Override
	public synchronized void send(Command command) {
		this.received.add(command);
		long now = System.currentTimeMillis();
		if (this.playing) {
			this.position += now - this.since;
		}

		this.since = now;
		switch (command) {
			case PLAY_PAUSE -> this.playing = !this.playing;
			case NEXT -> {
				this.track = (this.track + 1) % Math.max(1, this.tracks.size());
				this.position = 0L;
			}
			case PREVIOUS -> {
				this.track = (this.track - 1 + Math.max(1, this.tracks.size())) % Math.max(1, this.tracks.size());
				this.position = 0L;
			}
		}
	}

	@Override
	public String status() {
		return "test source";
	}
}
