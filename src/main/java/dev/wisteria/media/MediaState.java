package dev.wisteria.media;

/**
 * What is playing, as the system reported it at {@code receivedAtMillis}.
 *
 * @param app the player that owns the session, e.g. "Spotify.exe"
 * @param positionMillis the position at the moment the player last reported it
 * @param ageMillis how old that report already was when it arrived
 * @param durationMillis 0 if the player does not say
 * @param receivedAtMillis when it arrived, {@code System.currentTimeMillis()}
 */
public record MediaState(String app, String title, String artist, long positionMillis, long ageMillis, long durationMillis, boolean playing,
	long receivedAtMillis) {
	/** Where playback is now: the reported position moved on by the time since, while playing; never past the end. */
	public long positionAt(long nowMillis) {
		long position = this.positionMillis;
		if (this.playing) {
			position += Math.max(0L, this.ageMillis) + Math.max(0L, nowMillis - this.receivedAtMillis);
		}

		return this.durationMillis > 0L ? Math.clamp(position, 0L, this.durationMillis) : Math.max(0L, position);
	}

	/** 0 to 1, or 0 when the length is unknown. */
	public float progressAt(long nowMillis) {
		return this.durationMillis > 0L ? (float) this.positionAt(nowMillis) / this.durationMillis : 0.0F;
	}

	/** "3:07", "1:02:45". */
	public static String time(long millis) {
		long seconds = Math.max(0L, millis) / 1000L;
		long hours = seconds / 3600L;
		long minutes = seconds / 60L % 60L;
		return hours > 0L ? String.format("%d:%02d:%02d", hours, minutes, seconds % 60L) : String.format("%d:%02d", minutes, seconds % 60L);
	}
}
