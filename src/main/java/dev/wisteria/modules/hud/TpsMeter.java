package dev.wisteria.modules.hud;

import java.util.ArrayDeque;

/**
 * The server's ticks per second, from the game time it sends about once a second: game ticks elapsed over real
 * time elapsed between the first and last of the recent updates. Fed from the network thread.
 */
public final class TpsMeter {
	private static final int WINDOW = 6;

	private record Sample(long gameTime, long nanos) {
	}

	private final ArrayDeque<Sample> samples = new ArrayDeque<>();

	public synchronized void record(long gameTime, long nanos) {
		Sample last = this.samples.peekLast();
		if (last != null && gameTime < last.gameTime()) {
			// Time went backwards: another world, or /time set. Start over.
			this.samples.clear();
		}

		this.samples.addLast(new Sample(gameTime, nanos));
		while (this.samples.size() > WINDOW) {
			this.samples.removeFirst();
		}
	}

	public synchronized void reset() {
		this.samples.clear();
	}

	/** Ticks per second, at most 20; NaN until two updates have arrived. */
	public synchronized double tps() {
		if (this.samples.size() < 2) {
			return Double.NaN;
		}

		Sample first = this.samples.peekFirst();
		Sample last = this.samples.peekLast();
		double seconds = (last.nanos() - first.nanos()) / 1e9;
		if (seconds <= 0.0) {
			return Double.NaN;
		}

		return Math.min(20.0, (last.gameTime() - first.gameTime()) / seconds);
	}

	/** Seconds since the last update, or NaN if there has been none. */
	public synchronized double silence(long nanos) {
		Sample last = this.samples.peekLast();
		return last == null ? Double.NaN : (nanos - last.nanos()) / 1e9;
	}
}
