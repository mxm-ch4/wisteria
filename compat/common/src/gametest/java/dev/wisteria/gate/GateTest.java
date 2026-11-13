package dev.wisteria.gate;

import com.mojang.blaze3d.platform.InputConstants;
import dev.wisteria.Wisteria;
import dev.wisteria.compat.AudioBackend;
import dev.wisteria.compat.Platform;
import dev.wisteria.compat.Renderer2D;
import dev.wisteria.compat.ScreenBackend;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Screenshot;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The version gate (SPEC_AMENDMENT_2 §1): a Minecraft version counts as supported only if this passes on it.
 * It starts a real client, makes a world and exercises every compat interface the way core would.
 */
public final class GateTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("WisteriaGate");
	private static final String TONE = "/assets/wisteria-gate/gate_tone.ogg";

	@Override
	public void runTest(ClientGameTestContext context) {
		Platform platform = Wisteria.platform();
		LOG.info("gate: Minecraft {} on {}", platform.minecraftVersion(), platform.graphicsBackend());

		GateProbe probe = new GateProbe(platform.minecraftVersion());
		platform.listen(probe);
		platform.input().listen(probe);
		platform.packets().listen(probe);

		this.audio(context, platform);

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			GateHooks.waitForWorld(singleplayer);

			probe.captureTarget = true;
			context.waitTicks(20);
			LOG.info("3D target: {}", probe.targetInfo);
			Frame world = this.grabLastFrame(context, platform, "gate-world");
			float scale = world.width / probe.guiWidth;
			// Panel body is dark, its very corner is cut away by the corner shader, the accent dot is violet.
			check("2D: panel drawn", luminance(world.at(100.0F * scale, 30.0F * scale)) < 70);
			int body = world.at(100.0F * scale, 30.0F * scale);
			int corner = world.at(9.0F * scale, 9.0F * scale);
			int sky = world.at(2.0F * scale, 2.0F * scale);
			check("2D: corner rounded", distance(corner, sky) < 30 && distance(corner, body) > 60);
			check("2D: accent dot", isViolet(world.at((8.5F + 184.0F - 15.0F) * scale, (8.5F + 15.0F) * scale)));
			int boxPixels = world.count(world.width / 2 - 110, world.height / 2 - 90, 220, 180, GateTest::isViolet);
			LOG.info("3D: {} tinted pixels around the crosshair", boxPixels);
			check("3D: box visible in a real frame", boxPixels > 800);

			context.getInput().pressKey(InputConstants.KEY_G);
			context.waitTicks(5);
			check("key press reported by name", probe.keys.contains("G:PRESS"));
			check("key release reported by name", probe.keys.contains("G:RELEASE"));

			// The client sends this one every tick, on every supported version.
			probe.dropOutgoing = "ServerboundClientTickEndPacket";
			context.waitTicks(10);
			probe.dropOutgoing = null;
			check("outgoing packet cancelled", probe.cancelled.get() > 0);

			AtomicBoolean closed = new AtomicBoolean();
			context.runOnClient(minecraft -> platform.screens().open(new GateScreen(closed)));
			context.waitTicks(10);
			check("screen open", context.computeOnClient(minecraft -> platform.screens().isAnyOpen()));
			Frame screen = this.grabLastFrame(context, platform, "gate-screen");
			check("screen: panel drawn", luminance(screen.at(screen.width / 2.0F, screen.height / 2.0F - 4.0F * scale)) < 70);
			check("screen: accent button", isViolet(screen.at(screen.width / 2.0F - 80.0F * scale, screen.height / 2.0F + 20.0F * scale)));
			context.runOnClient(minecraft -> platform.screens().close());
			context.waitTicks(5);
			check("screen closed", !context.computeOnClient(minecraft -> platform.screens().isAnyOpen()) && closed.get());

			LOG.info("tick={} render2D={} render3D={}", probe.ticks.get(), probe.frames2D.get(), probe.frames3D.get());
			LOG.info("keys={}", probe.keys);
			LOG.info("packets: sent={} received={} cancelled={}", probe.sent.get(), probe.received.get(), probe.cancelled.get());
			LOG.info("sent kinds: {}", probe.sentNames.stream().sorted().toList());
			LOG.info("received kinds: {}", probe.receivedNames.size());

			check("tick", probe.ticks.get() > 0);
			check("render2D", probe.frames2D.get() > 0);
			check("render3D", probe.frames3D.get() > 0);
			check("packets sent", probe.sent.get() > 0);
			check("packets received", probe.received.get() > 0);
			probe.clearTarget();
		}

		LOG.info("gate passed: Minecraft {} on {}", platform.minecraftVersion(), platform.graphicsBackend());
	}

	/** 2 s at full volume, 2 s at a quarter, 2 s with the game's music slider at zero and a vanilla click on top. */
	private void audio(ClientGameTestContext context, Platform platform) {
		// Compat calls belong on the client thread, like everything core does; the test runs on its own thread.
		AudioBackend.Track track = context.computeOnClient(minecraft -> platform.audio().play(new AudioBackend.Source() {
			@Override
			public String name() {
				return "gate tone";
			}

			@Override
			public InputStream open() throws IOException {
				InputStream stream = GateTest.class.getResourceAsStream(TONE);
				if (stream == null) {
					throw new IOException("Missing " + TONE);
				}

				return stream;
			}
		}, 1.0F));
		LOG.info("audio: playing at 1.0");
		context.waitTicks(40);
		check("track active while playing", context.computeOnClient(minecraft -> track.isActive()));

		track.setVolume(0.25F);
		LOG.info("audio: volume 0.25");
		context.waitTicks(40);

		context.runOnClient(minecraft -> {
			minecraft.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0);
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		});
		track.setVolume(1.0F);
		LOG.info("audio: music slider 0, volume 1.0, vanilla UI click on top");
		context.waitTicks(40);
		check("track still active", context.computeOnClient(minecraft -> track.isActive()));

		track.stop();
		context.waitTicks(10);
		check("track stopped", !context.computeOnClient(minecraft -> track.isActive()));
	}

	/**
	 * Reads back the frame the player last saw and saves it to <game dir>/gate/<name>.png. The test API's own
	 * screenshot renders an extra frame outside the normal loop, which on 26.1 misses per-frame world drawing.
	 */
	private Frame grabLastFrame(ClientGameTestContext context, Platform platform, String name) {
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

	private static int distance(int a, int b) {
		return Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)) + Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF)) + Math.abs((a & 0xFF) - (b & 0xFF));
	}

	private static int luminance(int argb) {
		return ((argb >> 16 & 0xFF) * 3 + (argb >> 8 & 0xFF) * 6 + (argb & 0xFF)) / 10;
	}

	/** Our accent tints: red above green and blue well above it. Sky (b > g > r) and grass (g highest) never match. */
	private static boolean isViolet(int argb) {
		int r = argb >> 16 & 0xFF;
		int g = argb >> 8 & 0xFF;
		int b = argb & 0xFF;
		return r > g + 8 && b > g + 20;
	}

	private record Frame(int width, int height, int[] pixels) {
		int at(float x, float y) {
			int px = Math.max(0, Math.min(this.width - 1, Math.round(x)));
			int py = Math.max(0, Math.min(this.height - 1, Math.round(y)));
			return this.pixels[py * this.width + px];
		}

		int count(int x0, int y0, int w, int h, java.util.function.IntPredicate test) {
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

	private static void check(String what, boolean ok) {
		if (!ok) {
			throw new AssertionError("Gate failed: " + what);
		}

		LOG.info("ok: {}", what);
	}

	private record GateScreen(AtomicBoolean closed) implements ScreenBackend.ClientScreen {
		@Override
		public String title() {
			return "Wisteria gate";
		}

		@Override
		public void render(Renderer2D renderer, float mouseX, float mouseY, float tickDelta) {
			float width = 220.0F;
			float height = 96.0F;
			float x = (renderer.width() - width) / 2.0F;
			float y = (renderer.height() - height) / 2.0F;
			renderer.roundedRect(x, y, width, height, 12.0F, 0xCC17131F);
			renderer.roundedRect(x + 12.0F, y + 60.0F, 90.0F, 22.0F, 11.0F, GateProbe.VIOLET);
			renderer.roundedRect(x + 110.0F, y + 60.0F, 98.0F, 22.0F, 6.0F, 0xFF2E2740);
			String title = "Wisteria";
			renderer.text(title, x + (width - renderer.textWidth(title)) / 2.0F, y + 14.0F, GateProbe.PETAL, false);
			String subtitle = "blurred world behind, own shader on top";
			renderer.text(subtitle, x + (width - renderer.textWidth(subtitle)) / 2.0F, y + 30.0F, GateProbe.MUTED, false);
			renderer.text("accent", x + 40.0F, y + 67.0F, 0xFF0E0B16, false);
			renderer.text("surface", x + 140.0F, y + 67.0F, GateProbe.TEXT, false);
		}

		@Override
		public void onClose() {
			this.closed.set(true);
		}
	}
}
