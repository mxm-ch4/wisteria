package dev.wisteria.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/** Short notifications that slide in, stay a few seconds and fade out. Client thread. */
public final class Toasts {
	public static final long SHOW_NANOS = 3_000_000_000L;
	public static final long FADE_NANOS = 250_000_000L;
	public static final int MAX = 4;

	public enum Kind {
		INFO, ON, OFF, WARNING
	}

	public record Toast(String title, String text, Kind kind, long createdNanos) {
	}

	/** A toast as it is right now: how far it has come in (0 to 1) and how much time it has left (1 to 0). */
	public record Shown(Toast toast, float presence, float remaining) {
	}

	private final List<Toast> toasts = new ArrayList<>();
	private final LongSupplier clock;

	public Toasts(LongSupplier clock) {
		this.clock = clock;
	}

	private java.util.function.BooleanSupplier muted = () -> false;

	/** While {@code when} says so, new toasts are dropped (the panic button). */
	public void muteWhile(java.util.function.BooleanSupplier when) {
		this.muted = when;
	}

	/** Takes every toast off the screen at once. */
	public void clear() {
		this.toasts.clear();
	}

	public void push(String title, String text, Kind kind) {
		if (this.muted.getAsBoolean()) {
			return;
		}

		this.toasts.add(new Toast(title, text, kind, this.clock.getAsLong()));
		while (this.toasts.size() > MAX) {
			this.toasts.removeFirst();
		}
	}

	/** The toasts still on screen, newest last; forgets the ones that are done. */
	public List<Shown> shown() {
		long now = this.clock.getAsLong();
		this.toasts.removeIf(toast -> now - toast.createdNanos() > SHOW_NANOS);
		List<Shown> result = new ArrayList<>();
		for (Toast toast : this.toasts) {
			long age = now - toast.createdNanos();
			float in = Math.min(1.0F, age / (float)FADE_NANOS);
			float out = Math.min(1.0F, (SHOW_NANOS - age) / (float)FADE_NANOS);
			result.add(new Shown(toast, Math.max(0.0F, Math.min(in, out)), 1.0F - age / (float)SHOW_NANOS));
		}

		return result;
	}
}
