package dev.wisteria.gate;

import com.mojang.blaze3d.platform.InputConstants;
import static dev.wisteria.gate.GameTestKit.*;

import dev.wisteria.Client;
import dev.wisteria.Wisteria;
import dev.wisteria.platform.AudioBackend;
import dev.wisteria.platform.Key;
import dev.wisteria.platform.Platform;
import dev.wisteria.render.Renderer2D;
import dev.wisteria.platform.ScreenBackend;
import dev.wisteria.modules.clickgui.ClickGui;
import dev.wisteria.ui.SplashScreen;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;
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
		if ("restart".equals(System.getenv("WISTERIA_GATE_PHASE"))) {
			this.afterRestart(context, platform);
			return;
		}

		GateProbe probe = new GateProbe(platform.minecraftVersion());
		platform.listen(probe);
		platform.input().listen(probe);
		platform.packets().listen(probe);

		this.splash(context, platform);
		this.audio(context, platform);
		check("splash: gone, title screen is back", context.computeOnClient(minecraft -> GateHooks.currentScreen(minecraft) instanceof TitleScreen));

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			GateHooks.waitForWorld(singleplayer);

			probe.captureTarget = true;
			context.waitTicks(20);
			LOG.info("3D target: {}", probe.targetInfo);
			Frame world = grabLastFrame(context, platform, "gate-world");
			float scale = world.width() / probe.guiWidth;
			// Panel body is dark, its very corner is cut away by the corner shader, the accent dot is violet.
			check("2D: panel drawn", luminance(world.at(100.0F * scale, 30.0F * scale)) < 70);
			int body = world.at(100.0F * scale, 30.0F * scale);
			int corner = world.at(9.0F * scale, 9.0F * scale);
			int sky = world.at(2.0F * scale, 2.0F * scale);
			check("2D: corner rounded", distance(corner, sky) < 30 && distance(corner, body) > 60);
			check("2D: accent dot", isViolet(world.at((8.5F + 184.0F - 15.0F) * scale, (8.5F + 15.0F) * scale)));
			// A font that failed to load falls back to identical placeholder boxes, so every string is the same width.
			float[] widths = probe.fontWidths;
			LOG.info("fonts: iiii/WWWW widths regular {}/{}, semibold {}/{}, mono {}/{}", widths[0], widths[1], widths[2], widths[3], widths[4], widths[5]);
			check("fonts: Inter Regular loaded", widths[0] > 0.0F && widths[0] < widths[1] * 0.6F);
			check("fonts: Inter SemiBold loaded", widths[2] < widths[3] * 0.6F && widths[3] > widths[1]);
			check("fonts: JetBrains Mono loaded", widths[4] > 0.0F && Math.abs(widths[4] - widths[5]) < 0.01F && Math.abs(widths[4] - 4.0F * 9.6F) < 0.5F);
			// The glow is faint by design: compare the sky just right of the panel with the same sky further out.
			int nearPanel = world.at((8.5F + 184.0F + 2.0F) * scale, (84.5F + 29.0F) * scale);
			int farFromPanel = world.at((8.5F + 184.0F + 30.0F) * scale, (84.5F + 29.0F) * scale);
			int tint = redOverGreen(nearPanel) - redOverGreen(farFromPanel);
			LOG.info("2D: glow shifts red-over-green by {} next to the panel", tint);
			check("2D: glow outside the panel", tint > 5);
			check("2D: outline on the panel edge", isViolet(world.at(100.0F * scale, (84.5F + 0.5F) * scale)));

			int boxPixels = world.count(world.width() / 2 - 110, world.height() / 2 - 90, 220, 180, GameTestKit::isViolet);
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
			Frame screen = grabLastFrame(context, platform, "gate-screen");
			check("screen: panel drawn", luminance(screen.at(screen.width() / 2.0F, screen.height() / 2.0F - 4.0F * scale)) < 70);
			check("screen: accent button", isViolet(screen.at(screen.width() / 2.0F - 80.0F * scale, screen.height() / 2.0F + 20.0F * scale)));
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

			this.core(context, platform, probe, singleplayer, scale);
		}

		LOG.info("gate passed: Minecraft {} on {}", platform.minecraftVersion(), platform.graphicsBackend());
	}

	/**
	 * Stage 1 control, in the real game: modules switched from a chat command and from a bound key, tab
	 * completion in the chat box, and settings left behind for {@link #afterRestart} to find.
	 */
	private void core(ClientGameTestContext context, Platform platform, GateProbe probe, TestSingleplayerContext singleplayer, float scale) {
		Client client = Wisteria.client();
		check("core: modules registered", client.modules().all().size() == 14);
		check("core: starts with everything off", context.computeOnClient(minecraft -> client.modules().enabled().isEmpty()));

		// A command typed into chat: runs in the client and must not reach the server.
		probe.sentNames.clear();
		chat(context, ".toggle watermark on");
		chat(context, ".hud watermark bottom-right");
		context.waitTicks(5);
		check("core: command switched Watermark on", context.computeOnClient(minecraft -> client.modules().get("watermark").isEnabled()));
		check("core: command was not sent to the server", probe.sentNames.stream().noneMatch(name -> name.contains("Chat")));
		Frame hud = grabLastFrame(context, platform, "gate-core-hud");
		int labelPixels = hud.count(hud.width() - Math.round(110 * scale), hud.height() - Math.round(26 * scale), Math.round(110 * scale), Math.round(26 * scale), GameTestKit::isViolet);
		LOG.info("core: {} accent pixels in the watermark corner", labelPixels);
		check("core: Watermark visible in a real frame", labelPixels > 40);

		// Tab completion through the real chat box.
		context.getInput().pressKey(options -> options.keyChat);
		context.waitForScreen(ChatScreen.class);
		context.getInput().typeChars(".tog");
		context.waitTicks(2);
		// As with the game's own commands: the first Tab opens the list, the second takes the highlighted entry.
		context.getInput().pressKey(InputConstants.KEY_TAB);
		context.waitTicks(2);
		context.getInput().pressKey(InputConstants.KEY_TAB);
		context.waitTicks(2);
		String completed = context.computeOnClient(minecraft -> chatInput(GateHooks.currentScreen(minecraft)));
		LOG.info("core: \".tog\" + Tab, Tab -> \"{}\"", completed);
		check("core: Tab completes a client command", ".toggle".equals(completed));
		context.setScreen(() -> null);
		context.waitForScreen(null);

		// A bound key, pressed in the game.
		chat(context, ".bind esp g");
		chat(context, ".esp passive on");
		chat(context, ".esp passive-color #FF8B6CFF");
		chat(context, ".esp fill 120");
		chat(context, ".esp range 48");
		singleplayer.getServer().runCommand("summon minecraft:armor_stand 0.5 -60 4.5 {NoGravity:1b}");
		context.waitTicks(10);
		Frame before = grabLastFrame(context, platform, "gate-core-esp-off");
		int tintBefore = before.count(before.width() / 2 - 110, before.height() / 2 - 120, 220, 240, GameTestKit::isViolet);
		context.getInput().pressKey(InputConstants.KEY_G);
		context.waitTicks(10);
		check("core: bound key switched ESP on", context.computeOnClient(minecraft -> client.modules().get("esp").isEnabled()));
		Frame esp = grabLastFrame(context, platform, "gate-core-esp-on");
		int tintAfter = esp.count(esp.width() / 2 - 110, esp.height() / 2 - 120, 220, 240, GameTestKit::isViolet);
		LOG.info("core: tinted pixels around the armor stand: {} -> {}", tintBefore, tintAfter);
		check("core: ESP box visible in a real frame", tintAfter > tintBefore + 300);
		context.getInput().pressKey(InputConstants.KEY_G);
		context.waitTicks(5);
		check("core: bound key switched ESP off again", !context.computeOnClient(minecraft -> client.modules().get("esp").isEnabled()));


		// The design system reference screen, and a theme switch applied while it is open.
		singleplayer.getServer().runCommand("time set noon");
		chat(context, ".design");
		context.waitTicks(20);
		check("design: screen open", context.computeOnClient(minecraft -> platform.screens().isAnyOpen()));
		Frame design = grabLastFrame(context, platform, "gate-design");
		float panelX = Math.round((probe.guiWidth - 408.0F) / 2.0F);
		float panelY = Math.round((design.height() / scale - 222.0F) / 2.0F);
		int swatch = design.at((panelX + 61.0F) * scale, (panelY + 92.0F) * scale);
		LOG.info("design: accent swatch is #{}", Integer.toHexString(swatch));
		check("design: panel drawn", luminance(design.at((panelX + 204.0F) * scale, (panelY + 200.0F) * scale)) < 70);
		check("design: accent swatch in the theme's accent", distance(swatch, 0xFFB79CFF) < 30);
		chat(context, ".theme mint");
		context.waitTicks(5);
		Frame mint = grabLastFrame(context, platform, "gate-design-mint");
		int mintSwatch = mint.at((panelX + 61.0F) * scale, (panelY + 92.0F) * scale);
		LOG.info("design: after .theme mint the swatch is #{}", Integer.toHexString(mintSwatch));
		check("design: theme switch applies at once", distance(mintSwatch, 0xFF7FE3BC) < 30);
		context.runOnClient(minecraft -> platform.screens().close());
		context.waitTicks(5);

		this.clickGui(context, platform, client, scale);
		chat(context, ".theme mint");

		// Left for the second launch to verify.
		chat(context, ".friend add GateFriend");
		chat(context, ".profile save gate");
		context.waitTicks(5);
		check("core: profile switched", "gate".equals(context.computeOnClient(minecraft -> client.config().profile())));
		LOG.info("core checks passed; state left for the restart check");
	}

	/** The splash that plays over the title screen once the game has loaded. */
	private void splash(ClientGameTestContext context, Platform platform) {
		// The automatic one has already played by the time the test harness hands over, so check that it was
		// triggered and then play it again to look at it.
		Client client = Wisteria.client();
		check("splash: triggered when the title screen first appeared", client.isReady());
		context.runOnClient(minecraft -> platform.screens().open(new SplashScreen(client)));
		context.waitTicks(26);
		Frame frame = grabLastFrame(context, platform, "gate-splash");
		int blossoms = frame.count(frame.width() / 2 - 60, frame.height() / 2 - 140, 120, 200, GameTestKit::isViolet);
		LOG.info("splash: {} accent pixels in the mark", blossoms);
		check("splash: shown over the title screen", context.computeOnClient(minecraft -> !(GateHooks.currentScreen(minecraft) instanceof TitleScreen)));
		check("splash: mark drawn", blossoms > 1500);
		check("splash: dark backdrop", luminance(frame.at(20.0F, 20.0F)) < 40);
		context.waitTicks(30);
		check("splash: closes itself", context.computeOnClient(minecraft -> GateHooks.currentScreen(minecraft) instanceof TitleScreen));
	}

	/**
	 * Stage 3 control: the ClickGUI driven with a real cursor and real key presses. Layout facts used here:
	 * the Render panel starts at GUI (8, 32), its header is 22 tall, module rows are 17 tall.
	 */
	private void clickGui(ClientGameTestContext context, Platform platform, Client client, float scale) {
		context.getInput().pressKey(InputConstants.KEY_RSHIFT);
		context.waitTicks(15);
		check("gui: Right Shift opens it", context.computeOnClient(minecraft -> client.modules().get("clickgui").isEnabled() && platform.screens().isAnyOpen()));
		grabLastFrame(context, platform, "gate-clickgui");

		float firstRow = 32.0F + 22.0F + 1.0F;
		boolean fullBrightBefore = context.computeOnClient(minecraft -> client.modules().get("fullbright").isEnabled());
		click(context, 48.0F, firstRow + 8.5F, InputConstants.MOUSE_BUTTON_LEFT, scale);
		check("gui: left click toggles a module", context.computeOnClient(minecraft -> client.modules().get("fullbright").isEnabled()) != fullBrightBefore);
		click(context, 48.0F, firstRow + 8.5F, InputConstants.MOUSE_BUTTON_LEFT, scale);

		// Right click unfolds ESP; its Range slider sits under the Bind row.
		click(context, 48.0F, firstRow + 17.0F + 8.5F, InputConstants.MOUSE_BUTTON_RIGHT, scale);
		context.waitTicks(8);
		float sliderY = firstRow + 2.0F * 17.0F + 4.0F + 15.0F + 16.0F;
		context.getInput().setCursorPos((17.0F + 50.0F) * scale, sliderY * scale);
		context.waitTicks(2);
		context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		String half = context.computeOnClient(minecraft -> client.modules().get("esp").setting("range").format());
		context.getInput().setCursorPos((17.0F + 100.0F) * scale, sliderY * scale);
		context.waitTicks(3);
		context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		String full = context.computeOnClient(minecraft -> client.modules().get("esp").setting("range").format());
		LOG.info("gui: slider at half = {}, dragged to the end = {}", half, full);
		check("gui: clicking the slider sets the value", "132".equals(half));
		check("gui: dragging the slider follows the cursor", "256".equals(full));
		grabLastFrame(context, platform, "gate-clickgui-settings");
		chat(context, ".esp range 48");

		// Just typing searches; with one match left, it is the first row.
		boolean searchedBefore = context.computeOnClient(minecraft -> client.modules().get("fullbright").isEnabled());
		context.getInput().typeChars("fullb");
		context.waitTicks(4);
		grabLastFrame(context, platform, "gate-clickgui-search");
		click(context, 48.0F, firstRow + 8.5F, InputConstants.MOUSE_BUTTON_LEFT, scale);
		check("gui: search narrows the list to the match", context.computeOnClient(minecraft -> client.modules().get("fullbright").isEnabled()) != searchedBefore);
		click(context, 48.0F, firstRow + 8.5F, InputConstants.MOUSE_BUTTON_LEFT, scale);

		// The last theme dot on the status line.
		float guiWidth = context.computeOnClient(minecraft -> (float)minecraft.getWindow().getGuiScaledWidth());
		float guiHeight = context.computeOnClient(minecraft -> (float)minecraft.getWindow().getGuiScaledHeight());
		click(context, guiWidth - 10.0F - 15.0F + 4.5F, guiHeight - 15.0F + 5.5F, InputConstants.MOUSE_BUTTON_LEFT, scale);
		check("gui: a theme dot switches the theme", "frost".equals(context.computeOnClient(minecraft -> client.themes().presetId())));
		context.waitTicks(3);
		grabLastFrame(context, platform, "gate-clickgui-frost");

		double micros = context.computeOnClient(minecraft -> ((ClickGui)client.modules().get("clickgui")).screen().renderMicros());
		LOG.info("gui: {} microseconds of CPU per frame to lay out and submit the GUI", Math.round(micros));
		check("gui: frame cost stays far below a 60 fps frame (16667 us)", micros < 2000.0);

		context.getInput().pressKey(InputConstants.KEY_RSHIFT);
		context.waitTicks(5);
		check("gui: Right Shift closes it", context.computeOnClient(minecraft -> !client.modules().get("clickgui").isEnabled() && !platform.screens().isAnyOpen()));
	}

	/** Second launch of the same game directory: is everything the first launch set still there? */
	private void afterRestart(ClientGameTestContext context, Platform platform) {
		Client client = Wisteria.client();
		context.runOnClient(minecraft -> {
			check("restart: profile", "gate".equals(client.config().profile()));
			check("restart: Watermark still on", client.modules().get("watermark").isEnabled());
			check("restart: Watermark corner", client.hud().placement(client.hud().element("watermark")).anchor() == dev.wisteria.hud.Anchor.BOTTOM_RIGHT);
			check("restart: ESP off", !client.modules().get("esp").isEnabled());
			check("restart: ESP bind", client.modules().get("esp").bind().get().equals(Optional.of(Key.G)));
			check("restart: ESP range", "48".equals(client.modules().get("esp").setting("range").format()));
			check("restart: ESP colour", "#FF8B6CFF".equals(client.modules().get("esp").setting("passive-color").format()));
			check("restart: FullBright off", !client.modules().get("fullbright").isEnabled());
			check("restart: friend", client.relations().isFriend("GateFriend"));
			check("restart: theme", "mint".equals(client.themes().presetId()));
			check("restart: ClickGUI closed", !client.modules().get("clickgui").isEnabled());
			check("restart: panel layout remembered", client.gui().panel(dev.wisteria.module.Category.RENDER) != null);
		});
		// The splash may still be playing; the test harness wants to be handed back the title screen.
		context.waitFor(minecraft -> GateHooks.currentScreen(minecraft) instanceof TitleScreen);
		LOG.info("gate restart check passed: Minecraft {} on {}", platform.minecraftVersion(), platform.graphicsBackend());
	}

	// The chat box's text field is not public; this is a test, so read it by name.
	private static String chatInput(Object screen) {
		try {
			java.lang.reflect.Field field = ChatScreen.class.getDeclaredField("input");
			field.setAccessible(true);
			return ((EditBox)field.get(screen)).getValue();
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Could not read the chat input", e);
		}
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
