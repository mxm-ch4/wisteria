package dev.wisteria.ui;

import java.util.function.LongSupplier;

/**
 * One animated number: it eases from wherever it is to wherever it is told to go. This is the only place
 * interface motion comes from; widgets hold an Animation and read {@link #value()} each frame instead of
 * writing their own interpolation.
 *
 * <p>Time is real time, not ticks or frames, so motion looks the same at 30 and at 300 fps and keeps going
 * while the game is paused.
 */
public final class Animation {
	/** Hover and press feedback. */
	public static final long FAST = 120L;
	/** Most transitions: a toggle, a panel expanding. */
	public static final long NORMAL = 200L;
	/** A whole screen opening or closing. */
	public static final long SLOW = 350L;

	private static volatile LongSupplier clock = System::nanoTime;

	private final LongSupplier nanoClock;
	private final long durationNanos;
	private final Easing easing;
	private float from;
	private float to;
	private long startNanos;
	private boolean running;

	public Animation(float initial, long durationMillis, Easing easing) {
		this(initial, durationMillis, easing, () -> clock.getAsLong());
	}

	/** Replaces the clock of every animation made without an explicit one. For tests: jump time instead of sleeping. */
	public static void useClock(LongSupplier nanoClock) {
		clock = nanoClock;
	}

	/** Back to real time. */
	public static void useRealClock() {
		clock = System::nanoTime;
	}

	/** @param nanoClock a monotonic clock in nanoseconds; tests pass their own */
	public Animation(float initial, long durationMillis, Easing easing, LongSupplier nanoClock) {
		this.nanoClock = nanoClock;
		this.durationNanos = Math.max(1L, durationMillis) * 1_000_000L;
		this.easing = easing;
		this.from = initial;
		this.to = initial;
	}

	/** Starts moving towards the target from the current value, so a change of mind mid-flight never jumps. */
	public void animateTo(float target) {
		if (target == this.to) {
			return;
		}

		this.from = this.value();
		this.to = target;
		this.startNanos = this.nanoClock.getAsLong();
		this.running = true;
	}

	/** Shorthand for a 0..1 animation that follows a boolean state. */
	public void animateTo(boolean on) {
		this.animateTo(on ? 1.0F : 0.0F);
	}

	/** Jumps to the value with no motion: for initial state. */
	public void snapTo(float value) {
		this.from = value;
		this.to = value;
		this.running = false;
	}

	public float value() {
		if (!this.running) {
			return this.to;
		}

		float progress = (float)(this.nanoClock.getAsLong() - this.startNanos) / this.durationNanos;
		if (progress >= 1.0F) {
			this.running = false;
			return this.to;
		}

		return this.from + (this.to - this.from) * this.easing.apply(Math.max(0.0F, progress));
	}

	public float target() {
		return this.to;
	}

	public boolean isRunning() {
		// Reading the value is what notices the end of the run.
		this.value();
		return this.running;
	}
}
