package dev.wisteria.media;

import java.util.Optional;

/**
 * Where the media widget gets what is playing and sends its buttons. The Windows source reads the system media
 * controls (SMTC); tests use {@link FakeMediaSource}. Nothing here touches the network.
 */
public interface MediaSource {
	enum Command {
		PLAY_PAUSE, NEXT, PREVIOUS
	}

	/** Begin watching; called when the widget is switched on. May be called again after {@link #stop()}. */
	void start();

	/** Stop watching and let go of anything started. */
	void stop();

	/** What is playing now, or empty when nothing is (or the source is not available). */
	Optional<MediaState> current();

	/** Asks the player to do something; ignored when nothing is playing. */
	void send(Command command);

	/** One line for the menu: running, not available on this system, the last error. */
	String status();
}
