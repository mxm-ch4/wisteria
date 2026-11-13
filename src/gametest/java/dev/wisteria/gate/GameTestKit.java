package dev.wisteria.gate;

import dev.wisteria.platform.Platform;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Screenshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helpers for tests that run in the real client: commands, clicks, reading back the frame on screen,
 * pixel checks. See docs/module-api.md; FullBrightGameTest is the worked example.
 */
public final class GameTestKit {
	private static final Logger LOG = LoggerFactory.getLogger("WisteriaGate");

	private GameTestKit() {
	}

	/** True in the second launch of tools/gate.sh, which only checks that settings survived a restart. */
	public static boolean isRestartPhase() {
		return "restart".equals(System.getenv("WISTERIA_GATE_PHASE"));
	}

	/** A fresh world with its chunks rendered. Close it (try-with-resources) to get back to the title screen. */
	/** Hides the gate's own diagnostic panels (or shows them again), for frames that look at the HUD or the world. */
	public static void hideProbe(boolean hidden) {
		GateProbe probe = GateProbe.current;
		if (probe != null) {
			probe.hidden = hidden;
		}
	}

	/**
	 * The gate's last launch: the same client with Wisteria switched off (-Dwisteria.disabled=true), for the network
	 * audit to compare against. Every other test skips it.
	 */
	public static boolean isAuditPhase() {
		return "audit".equals(System.getenv("WISTERIA_GATE_PHASE"));
	}

	/** Where the gate's frames and files go: the game directory's gate folder. */
	public static java.nio.file.Path gateDirectory(ClientGameTestContext context) {
		return context.computeOnClient(minecraft -> minecraft.gameDirectory.toPath().resolve("gate"));
	}

	public static TestSingleplayerContext newWorld(ClientGameTestContext context) {
		TestSingleplayerContext world = context.worldBuilder().create();
		GateHooks.waitForWorld(world);
		return world;
	}

	/** GUI pixels to window pixels: multiply GUI coordinates by this to aim the cursor or read a frame. */
	public static float guiScale(ClientGameTestContext context) {
		return context.computeOnClient(minecraft -> (float)minecraft.getWindow().getWidth() / minecraft.getWindow().getGuiScaledWidth());
	}

	public static void click(ClientGameTestContext context, float guiX, float guiY, int button, float scale) {
		context.getInput().setCursorPos(guiX * scale, guiY * scale);
		context.waitTicks(2);
		context.getInput().pressMouse(button);
		context.waitTicks(3);
	}

	/** Sends a line the way the chat box does. */
	public static void chat(ClientGameTestContext context, String line) {
		context.runOnClient(minecraft -> minecraft.player.connection.sendChat(line));
		context.waitTick();
	}

	/**
	 * Reads back the frame the player last saw and saves it to <game dir>/gate/<name>.png. The test API's own
	 * screenshot renders an extra frame outside the normal loop, which on 26.1 misses per-frame world drawing.
	 */
	public static Frame grabLastFrame(ClientGameTestContext context, Platform platform, String name) {
		CompletableFuture<Frame> result = new CompletableFuture<>();
		Path file = platform.gameDirectory().resolve("gate").resolve(name + ".png");
		context.runOnClient(minecraft -> Screenshot.takeScreenshot(GateHooks.mainTarget(minecraft), image -> {
			try (image) {
				Files.createDirectories(file.getParent());
				image.writeToFile(file);
				int width = image.getWidth();
				int height = image.getHeight();
				int[] pixels = new int[width * height];
				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++) {
						pixels[y * width + x] = image.getPixel(x, y);
					}
				}

				result.complete(new Frame(width, height, pixels));
			} catch (Exception e) {
				result.completeExceptionally(e);
			}
		}));

		for (int i = 0; i < 200 && !result.isDone(); i++) {
			context.waitTick();
		}

		return result.join();
	}

	public static int distance(int a, int b) {
		return Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)) + Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF)) + Math.abs((a & 0xFF) - (b & 0xFF));
	}

	public static int redOverGreen(int argb) {
		return (argb >> 16 & 0xFF) - (argb >> 8 & 0xFF);
	}

	public static int luminance(int argb) {
		return ((argb >> 16 & 0xFF) * 3 + (argb >> 8 & 0xFF) * 6 + (argb & 0xFF)) / 10;
	}

	/** Our accent tints: red above green and blue well above it. Sky (b > g > r) and grass (g highest) never match. */
	public static boolean isViolet(int argb) {
		int r = argb >> 16 & 0xFF;
		int g = argb >> 8 & 0xFF;
		int b = argb & 0xFF;
		return r > g + 8 && b > g + 20;
	}

	public record Frame(int width, int height, int[] pixels) {
		public int at(float x, float y) {
			int px = Math.max(0, Math.min(this.width - 1, Math.round(x)));
			int py = Math.max(0, Math.min(this.height - 1, Math.round(y)));
			return this.pixels[py * this.width + px];
		}

		public int meanLuminance(int x0, int y0, int w, int h) {
			long sum = 0;
			int n = 0;
			for (int y = Math.max(0, y0); y < Math.min(this.height, y0 + h); y++) {
				for (int x = Math.max(0, x0); x < Math.min(this.width, x0 + w); x++) {
					sum += luminance(this.pixels[y * this.width + x]);
					n++;
				}
			}

			return n == 0 ? 0 : (int)(sum / n);
		}

		/**
		 * Share of horizontal ink edges in a region that jump straight from background to full ink with no
		 * in-between pixel. Smoothly rasterised text stays well below 0.3; text sampled with NEAREST scores about 0.5.
		 */
		public double hardEdgeShare(int x0, int y0, int w, int h) {
			int[] values = new int[w * h];
			int n = 0;
			for (int y = y0; y < y0 + h; y++) {
				for (int x = x0; x < x0 + w; x++) {
					values[n++] = luminance(this.at(x, y));
				}
			}

			int[] sorted = values.clone();
			java.util.Arrays.sort(sorted);
			double background = sorted[sorted.length / 10];
			double ink = sorted[Math.min(sorted.length - 1, sorted.length * 995 / 1000)];
			double range = Math.max(1.0, ink - background);
			int hard = 0;
			int edges = 0;
			for (int y = 0; y < h; y++) {
				for (int x = 1; x < w; x++) {
					double left = (values[y * w + x - 1] - background) / range;
					double right = (values[y * w + x] - background) / range;
					if (Math.abs(right - left) > 0.12) {
						edges++;
						if (Math.min(left, right) < 0.12 && Math.max(left, right) > 0.88) {
							hard++;
						}
					}
				}
			}

			return edges == 0 ? 0.0 : (double)hard / edges;
		}

		public int count(int x0, int y0, int w, int h, java.util.function.IntPredicate test) {
			int matches = 0;
			for (int y = Math.max(0, y0); y < Math.min(this.height, y0 + h); y++) {
				for (int x = Math.max(0, x0); x < Math.min(this.width, x0 + w); x++) {
					if (test.test(this.pixels[y * this.width + x])) {
						matches++;
					}
				}
			}

			return matches;
		}
	}

	/** Fails the test with the message if {@code ok} is false; logs "ok: ..." otherwise. */
	public static void check(String what, boolean ok) {
		if (!ok) {
			throw new AssertionError("Gate failed: " + what);
		}

		LOG.info("ok: {}", what);
	}
}
