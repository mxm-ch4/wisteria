package dev.wisteria.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Windows system media controls (SMTC: what the volume flyout shows) through a helper process: Windows PowerShell,
 * which ships with Windows 10 and 11 and can call the WinRT media API directly. The helper prints one JSON line twice a
 * second and takes {@code toggle}, {@code next}, {@code previous} on its input. It never asks for the cover art and
 * makes no network request; it ends when the widget is switched off or the game exits.
 *
 * <p>Why a helper and not JNA or FFM: the API is WinRT, so a native binding would mean writing the COM interfaces,
 * HSTRINGs and async callbacks by hand; PowerShell's WinRT projection does all of that, with nothing to ship.
 */
public final class SmtcMediaSource implements MediaSource {
	/** The helper. Kept short and readable: it is the whole Windows side. */
	static final String SCRIPT = """
		$ErrorActionPreference = 'Stop'
		Add-Type -AssemblyName System.Runtime.WindowsRuntime
		$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
		  $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
		function Await($operation, [Type]$type) {
		  $task = $asTask.MakeGenericMethod($type).Invoke($null, @($operation))
		  $task.Wait(-1) | Out-Null
		  $task.Result
		}
		$managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]
		$propertiesType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties,Windows.Media.Control,ContentType=WindowsRuntime]
		$manager = Await ($managerType::RequestAsync()) $managerType
		$input = [Console]::In
		$line = $input.ReadLineAsync()
		while ($true) {
		  $session = $manager.GetCurrentSession()
		  if ($line.IsCompleted) {
		    $command = $line.Result
		    if ($command -eq $null -or $command -eq 'quit') { break }
		    if ($session -ne $null) {
		      if ($command -eq 'toggle') { Await ($session.TryTogglePlayPauseAsync()) ([bool]) | Out-Null }
		      if ($command -eq 'next') { Await ($session.TrySkipNextAsync()) ([bool]) | Out-Null }
		      if ($command -eq 'previous') { Await ($session.TrySkipPreviousAsync()) ([bool]) | Out-Null }
		    }
		    $line = $input.ReadLineAsync()
		  }
		  if ($session -eq $null) {
		    [Console]::Out.WriteLine('{"active":false}')
		  } else {
		    $media = Await ($session.TryGetMediaPropertiesAsync()) $propertiesType
		    $timeline = $session.GetTimelineProperties()
		    $playback = $session.GetPlaybackInfo()
		    $state = [ordered]@{
		      active = $true; app = [string]$session.SourceAppUserModelId; title = [string]$media.Title; artist = [string]$media.Artist
		      position = [long]$timeline.Position.TotalMilliseconds; duration = [long]($timeline.EndTime - $timeline.StartTime).TotalMilliseconds
		      age = [long]([DateTimeOffset]::Now - $timeline.LastUpdatedTime).TotalMilliseconds
		      playing = ([string]$playback.PlaybackStatus -eq 'Playing')
		    }
		    [Console]::Out.WriteLine(($state | ConvertTo-Json -Compress))
		  }
		  [Console]::Out.Flush()
		  Start-Sleep -Milliseconds 500
		}
		""";

	private static final int MAX_RESTARTS = 3;

	private @Nullable Process process;
	private @Nullable Writer commands;
	private volatile @Nullable MediaState state;
	private volatile String status = "off";
	private volatile boolean wanted;
	private int restarts;

	/** True on Windows, the only system with SMTC. */
	public static boolean available() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
	}

	@Override
	public synchronized void start() {
		this.wanted = true;
		this.restarts = 0;
		this.launch();
	}

	private synchronized void launch() {
		if (!available()) {
			this.status = "not available: needs Windows";
			return;
		}

		if (this.process != null && this.process.isAlive()) {
			return;
		}

		String encoded = Base64.getEncoder().encodeToString(SCRIPT.getBytes(StandardCharsets.UTF_16LE));
		ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
			"-WindowStyle", "Hidden", "-EncodedCommand", encoded);
		builder.redirectError(ProcessBuilder.Redirect.DISCARD);
		try {
			Process started = builder.start();
			this.process = started;
			this.commands = new OutputStreamWriter(started.getOutputStream(), StandardCharsets.UTF_8);
			this.status = "starting";
			Thread reader = new Thread(() -> this.read(started), "Wisteria media");
			reader.setDaemon(true);
			reader.start();
		} catch (IOException failed) {
			this.status = "could not start PowerShell: " + failed.getMessage();
		}
	}

	private void read(Process started) {
		try (BufferedReader lines = new BufferedReader(new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = lines.readLine()) != null) {
				this.accept(line, System.currentTimeMillis());
			}
		} catch (IOException ended) {
			// the helper went away; handled below
		}

		this.state = null;
		synchronized (this) {
			if (this.process == started && this.wanted) {
				this.process = null;
				if (++this.restarts <= MAX_RESTARTS) {
					this.status = "helper ended; restarting";
					this.launch();
				} else {
					this.status = "helper keeps ending; switch the widget off and on to retry";
				}
			}
		}
	}

	/** One line from the helper. Package-private for the unit test. */
	void accept(String line, long now) {
		MediaState parsed = parse(line, now);
		this.state = parsed;
		this.status = parsed == null ? "running, nothing playing" : "running: " + parsed.app();
	}

	/** A helper line as a state; null for "nothing playing" or a line it cannot read. */
	static @Nullable MediaState parse(String line, long now) {
		try {
			JsonObject json = JsonParser.parseString(line).getAsJsonObject();
			if (!json.has("active") || !json.get("active").getAsBoolean()) {
				return null;
			}

			return new MediaState(text(json, "app"), text(json, "title"), text(json, "artist"), number(json, "position"), number(json, "age"),
				number(json, "duration"), json.has("playing") && json.get("playing").getAsBoolean(), now);
		} catch (RuntimeException unreadable) {
			return null;
		}
	}

	private static String text(JsonObject json, String key) {
		return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
	}

	private static long number(JsonObject json, String key) {
		return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsLong() : 0L;
	}

	@Override
	public synchronized void stop() {
		this.wanted = false;
		Process running = this.process;
		this.process = null;
		this.state = null;
		this.status = "off";
		if (running != null) {
			try {
				if (this.commands != null) {
					this.commands.write("quit\n");
					this.commands.flush();
				}
			} catch (IOException ignored) {
				// already gone
			}

			running.destroy();
		}

		this.commands = null;
	}

	@Override
	public Optional<MediaState> current() {
		return Optional.ofNullable(this.state);
	}

	@Override
	public synchronized void send(Command command) {
		if (this.commands == null) {
			return;
		}

		String word = switch (command) {
			case PLAY_PAUSE -> "toggle";
			case NEXT -> "next";
			case PREVIOUS -> "previous";
		};
		try {
			this.commands.write(word + "\n");
			this.commands.flush();
		} catch (IOException gone) {
			this.status = "helper not answering";
		}
	}

	@Override
	public String status() {
		return this.status;
	}
}
