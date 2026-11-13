package dev.wisteria.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wisteria.Client;
import dev.wisteria.FakePlatform;
import dev.wisteria.render.BoxBatch;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoxBenchTest {
	@TempDir
	Path game;

	@Test
	void summarizesAverageFpsAndWorstFrame() {
		long ms = 1_000_000L;
		BoxBench.Result result = BoxBench.summarize(new long[] {10 * ms, 10 * ms, 10 * ms, 30 * ms});
		assertEquals(4, result.frames());
		assertEquals(66.67, result.averageFps(), 0.01);
		assertEquals(30.0, result.worstMillis(), 1e-9);
		assertEquals("Bench: 10000 boxes, 4 frames on OpenGL: 67 fps average, worst frame 30.0 ms",
			BoxBench.format(10_000, result, "OpenGL"));
		assertEquals(0, BoxBench.summarize(new long[0]).frames());
	}

	@Test
	void fillsACubeAroundTheCamera() {
		BoxBatch batch = new BoxBatch(1.5F, true);
		BoxBench.fill(batch, 10_000, 100.5, 64.0, -20.0);
		assertEquals(10_000, batch.size());
		double minX = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		for (int i = 0; i < batch.size(); i++) {
			minX = Math.min(minX, batch.originX() + batch.bound(i, 0));
			maxX = Math.max(maxX, batch.originX() + batch.bound(i, 3));
		}

		assertTrue(minX < 100 && maxX > 101, "the camera is inside the cube: " + minX + ".." + maxX);
	}

	@Test
	void checksItsArguments() {
		FakePlatform platform = new FakePlatform(this.game);
		new Client(platform);
		platform.typeInChat(".bench");
		assertTrue(platform.chatLines.getLast().contains("Usage"), platform.chatLines.getLast());
		platform.typeInChat(".bench boxes lots");
		assertTrue(platform.chatLines.getLast().contains("not a number"), platform.chatLines.getLast());
		platform.typeInChat(".bench boxes 0");
		assertTrue(platform.chatLines.getLast().contains("from 1 to 200000"), platform.chatLines.getLast());
		platform.worldLoaded = false;
		platform.typeInChat(".bench boxes 100");
		assertTrue(platform.chatLines.getLast().contains("Join a world"), platform.chatLines.getLast());
		platform.worldLoaded = true;
		platform.typeInChat(".bench boxes 100");
		assertTrue(platform.chatLines.getLast().startsWith("Bench: 100 boxes for 5 seconds"), platform.chatLines.getLast());
		platform.typeInChat(".bench boxes 100");
		assertTrue(platform.chatLines.getLast().contains("already running"), platform.chatLines.getLast());
	}
}
