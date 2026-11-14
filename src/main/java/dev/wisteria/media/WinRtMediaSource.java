package dev.wisteria.media;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;
import org.jspecify.annotations.Nullable;

/**
 * Windows system media controls (SMTC: what the volume flyout shows), read inside the game's own process through Java's
 * foreign function API: no helper program, no console window, no native library of our own. A daemon thread joins the
 * COM multithreaded apartment, asks the session manager twice a second for the current session, and sends play/pause,
 * next and previous to it. It never asks for the cover art and makes no network request; it runs only while the
 * widget is on.
 *
 * <p>The official launcher starts the game with {@code --enable-native-access=ALL-UNNAMED}, so this prints no warning.
 */
public final class WinRtMediaSource implements MediaSource {
	static final String RUNTIME_CLASS = "Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager";
	/** IGlobalSystemMediaTransportControlsSessionManagerStatics: RequestAsync 6. */
	static final String IID_MANAGER_STATICS = "2050c4ee-11a0-57de-aed7-c97c70338245";
	static final int STATICS_REQUEST_ASYNC = 6;
	/** IGlobalSystemMediaTransportControlsSessionManager: GetCurrentSession 6. */
	static final int MANAGER_CURRENT_SESSION = 6;
	/**
	 * IGlobalSystemMediaTransportControlsSession: SourceAppUserModelId 6, TryGetMediaPropertiesAsync 7,
	 * GetTimelineProperties 8, GetPlaybackInfo 9, TryPlay 10, TryPause 11, TryStop 12, TryRecord 13, TryFastForward 14,
	 * TryRewind 15, TrySkipNext 16, TrySkipPrevious 17, TryChangeChannelUp 18, TryChangeChannelDown 19,
	 * TryTogglePlayPause 20.
	 */
	static final int SESSION_APP = 6;
	static final int SESSION_MEDIA_PROPERTIES = 7;
	static final int SESSION_TIMELINE = 8;
	static final int SESSION_PLAYBACK_INFO = 9;
	static final int SESSION_SKIP_NEXT = 16;
	static final int SESSION_SKIP_PREVIOUS = 17;
	static final int SESSION_TOGGLE_PLAY_PAUSE = 20;
	/** IGlobalSystemMediaTransportControlsSessionMediaProperties: Title 6, Subtitle 7, AlbumArtist 8, Artist 9. */
	static final int PROPERTIES_TITLE = 6;
	static final int PROPERTIES_ARTIST = 9;
	/** …TimelineProperties: StartTime 6, EndTime 7, MinSeekTime 8, MaxSeekTime 9, Position 10, LastUpdatedTime 11. */
	static final int TIMELINE_START = 6;
	static final int TIMELINE_END = 7;
	static final int TIMELINE_POSITION = 10;
	static final int TIMELINE_LAST_UPDATED = 11;
	/** …PlaybackInfo: Controls 6, PlaybackStatus 7. Status: Closed 0, Opened 1, Changing 2, Stopped 3, Playing 4, Paused 5. */
	static final int PLAYBACK_STATUS = 7;
	static final int STATUS_PLAYING = 4;

	private static final long POLL_NANOS = 500_000_000L;
	private static final long ASYNC_TIMEOUT_MS = 3_000L;

	private final ConcurrentLinkedQueue<Command> commands = new ConcurrentLinkedQueue<>();
	private volatile @Nullable MediaState state;
	private volatile String status = "off";
	private volatile boolean running;
	private @Nullable Thread worker;
	/** Each start gets its own token; a worker left over from an earlier start sees a different one and ends. */
	private volatile Object generation = new Object();

	/** True on Windows, the only system with SMTC. */
	public static boolean available() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
	}

	@Override
	public synchronized void start() {
		if (!available()) {
			this.status = "not available: needs Windows";
			return;
		}

		if (this.running) {
			return;
		}

		this.running = true;
		this.status = "starting";
		Object token = new Object();
		this.generation = token;
		Thread thread = new Thread(() -> this.run(token), "Wisteria media");
		thread.setDaemon(true);
		this.worker = thread;
		thread.start();
	}

	@Override
	public synchronized void stop() {
		this.running = false;
		this.generation = new Object();
		this.state = null;
		this.commands.clear();
		Thread thread = this.worker;
		this.worker = null;
		if (thread != null) {
			LockSupport.unpark(thread);
		}

		this.status = "off";
	}

	@Override
	public Optional<MediaState> current() {
		return Optional.ofNullable(this.state);
	}

	@Override
	public void send(Command command) {
		Thread thread = this.worker;
		if (this.running && thread != null) {
			this.commands.add(command);
			LockSupport.unpark(thread);
		}
	}

	@Override
	public String status() {
		return this.status;
	}

	private boolean current(Object token) {
		return this.running && this.generation == token;
	}

	private void run(Object token) {
		Arena arena = Arena.ofShared();
		WinRt winRt = null;
		boolean joined = false;
		MemorySegment manager = MemorySegment.NULL;
		try {
			winRt = new WinRt(arena);
			winRt.initialize();
			joined = true;
			MemorySegment factory = winRt.activationFactory(arena, RUNTIME_CLASS, IID_MANAGER_STATICS);
			try {
				MemorySegment request = WinRt.callPointer(arena, factory, STATICS_REQUEST_ASYNC, "RequestAsync");
				manager = WinRt.await(arena, request, ASYNC_TIMEOUT_MS);
			} finally {
				WinRt.release(factory);
			}

			if (manager.address() == 0L) {
				this.status = "Windows did not give the media session manager";
				return;
			}

			while (this.current(token)) {
				try (Arena tick = Arena.ofConfined()) {
					this.runCommands(tick, manager);
					MediaState now = this.read(tick, winRt, manager);
					if (this.current(token)) {
						this.state = now;
						this.status = now == null ? "running, nothing playing" : "running: " + now.app();
					}
				}

				LockSupport.parkNanos(POLL_NANOS);
			}
		} catch (RuntimeException | LinkageError failed) {
			if (this.generation == token) {
				this.status = "stopped: " + failed.getMessage();
				this.state = null;
			}
		} finally {
			WinRt.release(manager);
			if (joined) {
				winRt.uninitialize();
			}

			arena.close();
		}
	}

	private void runCommands(Arena tick, MemorySegment manager) {
		Command command;
		while ((command = this.commands.poll()) != null) {
			MemorySegment session = WinRt.callPointer(tick, manager, MANAGER_CURRENT_SESSION, "GetCurrentSession");
			if (session.address() == 0L) {
				continue;
			}

			try {
				int slot = switch (command) {
					case PLAY_PAUSE -> SESSION_TOGGLE_PLAY_PAUSE;
					case NEXT -> SESSION_SKIP_NEXT;
					case PREVIOUS -> SESSION_SKIP_PREVIOUS;
				};
				// The returned IAsyncOperation<bool> goes on by itself; we only let go of it.
				WinRt.release(WinRt.callPointer(tick, session, slot, "media command"));
			} finally {
				WinRt.release(session);
			}
		}
	}

	private @Nullable MediaState read(Arena tick, WinRt winRt, MemorySegment manager) {
		MemorySegment session = WinRt.callPointer(tick, manager, MANAGER_CURRENT_SESSION, "GetCurrentSession");
		if (session.address() == 0L) {
			return null;
		}

		try {
			String app = winRt.stringProperty(tick, session, SESSION_APP);
			String title = "";
			String artist = "";
			MemorySegment properties = WinRt.await(tick, WinRt.callPointer(tick, session, SESSION_MEDIA_PROPERTIES, "TryGetMediaPropertiesAsync"),
				ASYNC_TIMEOUT_MS);
			if (properties.address() != 0L) {
				try {
					title = winRt.stringProperty(tick, properties, PROPERTIES_TITLE);
					artist = winRt.stringProperty(tick, properties, PROPERTIES_ARTIST);
				} finally {
					WinRt.release(properties);
				}
			}

			long start = 0L;
			long end = 0L;
			long position = 0L;
			long updated = 0L;
			MemorySegment timeline = WinRt.callPointer(tick, session, SESSION_TIMELINE, "GetTimelineProperties");
			if (timeline.address() != 0L) {
				try {
					start = longProperty(tick, timeline, TIMELINE_START);
					end = longProperty(tick, timeline, TIMELINE_END);
					position = longProperty(tick, timeline, TIMELINE_POSITION);
					updated = longProperty(tick, timeline, TIMELINE_LAST_UPDATED);
				} finally {
					WinRt.release(timeline);
				}
			}

			int playback = 0;
			MemorySegment info = WinRt.callPointer(tick, session, SESSION_PLAYBACK_INFO, "GetPlaybackInfo");
			if (info.address() != 0L) {
				try {
					MemorySegment out = tick.allocate(ValueLayout.JAVA_INT);
					WinRt.check(WinRt.callOut(info, PLAYBACK_STATUS, out), "PlaybackStatus");
					playback = out.get(ValueLayout.JAVA_INT, 0);
				} finally {
					WinRt.release(info);
				}
			}

			long now = System.currentTimeMillis();
			return state(app, title, artist, start, end, position, updated, playback, now);
		} finally {
			WinRt.release(session);
		}
	}

	private static long longProperty(Arena tick, MemorySegment object, int slot) {
		MemorySegment out = tick.allocate(ValueLayout.JAVA_LONG);
		WinRt.check(WinRt.callOut(object, slot, out), "timeline property " + slot);
		return out.get(ValueLayout.JAVA_LONG, 0);
	}

	/** The widget's state from the raw SMTC values (TimeSpans and DateTime in 100 ns ticks). Pure, for the unit test. */
	static @Nullable MediaState state(String app, String title, String artist, long startTicks, long endTicks, long positionTicks,
		long updatedTicks, int playbackStatus, long nowMillis) {
		if (title.isBlank() && artist.isBlank()) {
			return null;
		}

		long duration = Math.max(0L, WinRt.ticksToMillis(endTicks - startTicks));
		long position = Math.max(0L, WinRt.ticksToMillis(positionTicks - startTicks));
		return new MediaState(app, title, artist, position, WinRt.ageMillis(updatedTicks, nowMillis), duration, playbackStatus == STATUS_PLAYING,
			nowMillis);
	}
}
