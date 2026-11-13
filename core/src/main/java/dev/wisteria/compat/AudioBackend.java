package dev.wisteria.compat;

import java.io.IOException;
import java.io.InputStream;

/** Plays the client's own audio next to the game's. */
public interface AudioBackend {
	/**
	 * Starts streaming an Ogg Vorbis source. The track is not positional and is unaffected by the game's
	 * music slider; the master slider still applies.
	 *
	 * @param volume 0..1
	 */
	Track play(Source source, float volume);

	interface Source {
		/** A short name for logs. */
		String name();

		/** Opens a fresh Ogg Vorbis stream; the backend closes it. */
		InputStream open() throws IOException;
	}

	interface Track {
		void setVolume(float volume);

		float volume();

		void stop();

		/** True from {@link AudioBackend#play} until the stream ends or {@link #stop()} takes effect. */
		boolean isActive();
	}
}
