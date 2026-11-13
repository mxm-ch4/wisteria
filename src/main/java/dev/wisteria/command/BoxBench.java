package dev.wisteria.command;

import dev.wisteria.Client;
import dev.wisteria.event.EventBus;
import dev.wisteria.event.Events;
import dev.wisteria.render.BoxBatch;
import java.util.Arrays;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * {@code .bench boxes <N>}: draws N boxes as one {@link BoxBatch} around the camera, through walls (the Xray case),
 * and reports the frame rate over five seconds, so the owner can check the renderer on their own graphics card.
 * Frames are timed between consecutive Render3D events on the client thread.
 */
final class BoxBench {
	static final int MAX_BOXES = 200_000;
	static final long MEASURE_NANOS = 5_000_000_000L;
	/** Frames skipped first: the upload of the batch lands in them. */
	static final int WARMUP_FRAMES = 10;

	private final Client client;
	private final Command.Output out;
	private final int count;
	private final BoxBatch batch = new BoxBatch(1.5F, true);
	private final long[] frames = new long[20_000];
	private int frameCount;
	private int seen;
	private long last;
	private long start;
	private boolean done;
	private EventBus.@Nullable Subscription<Events.Render3D> subscription;
	private EventBus.@Nullable Subscription<Events.Leave> leave;

	private BoxBench(Client client, Command.Output out, int count) {
		this.client = client;
		this.out = out;
		this.count = count;
	}

	/** True until the run has reported or was cancelled. */
	boolean isRunning() {
		return this.subscription != null;
	}

	static BoxBench start(Client client, Command.Output out, int count) {
		BoxBench bench = new BoxBench(client, out, count);
		bench.subscription = client.bus().subscribe(Events.Render3D.class, bench::frame);
		bench.leave = client.bus().subscribe(Events.Leave.class, event -> {
			bench.end();
			out.print("Bench cancelled: left the world");
		});
		out.print("Bench: " + count + " boxes for " + MEASURE_NANOS / 1_000_000_000L + " seconds; keep the camera still");
		return bench;
	}

	private void frame(Events.Render3D event) {
		if (this.done) {
			return;
		}

		if (this.batch.size() == 0) {
			// 2 blocks apart, in a cube centred on the camera: boxes in every direction, as Xray would have them.
			fill(this.batch, this.count, event.renderer().cameraX(), event.renderer().cameraY(), event.renderer().cameraZ());
		}

		event.renderer().boxes(this.batch);
		long now = System.nanoTime();
		this.seen++;
		if (this.seen <= WARMUP_FRAMES) {
			this.last = now;
			this.start = now;
			return;
		}

		if (this.frameCount < this.frames.length) {
			this.frames[this.frameCount++] = now - this.last;
		}

		this.last = now;
		if (now - this.start >= MEASURE_NANOS) {
			this.done = true;
			this.finish();
		}
	}

	private void finish() {
		Result result = summarize(Arrays.copyOf(this.frames, this.frameCount));
		// Not from inside the handler that is being dispatched.
		this.client.platform().execute(() -> {
			this.end();
			this.out.print(format(this.count, result, this.client.platform().graphicsBackend()));
		});
	}

	private void end() {
		if (this.subscription != null) {
			this.client.bus().unsubscribe(this.subscription);
			this.subscription = null;
		}

		if (this.leave != null) {
			this.client.bus().unsubscribe(this.leave);
			this.leave = null;
		}
	}

	static void fill(BoxBatch batch, int count, double cameraX, double cameraY, double cameraZ) {
		int side = (int)Math.ceil(Math.cbrt(count));
		double half = side - 1.0;
		for (int i = 0; i < count; i++) {
			double x = Math.floor(cameraX) - half + i % side * 2;
			double y = Math.floor(cameraY) - half + i / side % side * 2;
			double z = Math.floor(cameraZ) - half + i / (side * side) * 2;
			batch.add(x, y, z, x + 1, y + 1, z + 1, 0xFFB79CFF, 0x40B79CFF);
		}
	}

	/** Average frame rate and the worst frame of a run; pure, for the unit test. */
	record Result(int frames, double averageFps, double worstMillis) {
	}

	static Result summarize(long[] frameNanos) {
		if (frameNanos.length == 0) {
			return new Result(0, 0.0, 0.0);
		}

		long total = 0L;
		long worst = 0L;
		for (long frame : frameNanos) {
			total += frame;
			worst = Math.max(worst, frame);
		}

		return new Result(frameNanos.length, frameNanos.length * 1e9 / total, worst / 1e6);
	}

	static String format(int count, Result result, String backend) {
		return String.format(Locale.ROOT, "Bench: %d boxes, %d frames on %s: %.0f fps average, worst frame %.1f ms",
			count, result.frames(), backend, result.averageFps(), result.worstMillis());
	}
}
