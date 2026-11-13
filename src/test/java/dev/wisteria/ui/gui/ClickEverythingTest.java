package dev.wisteria.ui.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wisteria.Client;
import dev.wisteria.FakePlatform;
import dev.wisteria.FakeRenderer;
import dev.wisteria.platform.Key;
import dev.wisteria.platform.ScreenBackend;
import dev.wisteria.platform.ScreenBackend.MouseButton;
import dev.wisteria.ui.Animation;
import dev.wisteria.ui.DesignScreen;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every control on the ClickGUI and the design screen must respond to a click. The same walk runs in the real
 * game in the gate; here it is fast enough to run on every build.
 */
class ClickEverythingTest {
	@TempDir
	Path gameDirectory;

	private final AtomicLong clock = new AtomicLong(1_000_000_000L);

	@BeforeEach
	void fastAnimations() {
		Animation.useClock(this.clock::get);
	}

	@AfterEach
	void realAnimations() {
		Animation.useRealClock();
	}

	/** Drives a client screen directly: each action is followed by a jump in time and two frames. */
	private final class Direct implements ControlWalker.Driver {
		private final ScreenBackend.ClientScreen screen;
		private final Controls controls;
		private float mouseX;
		private float mouseY;

		Direct(ScreenBackend.ClientScreen screen, Controls controls) {
			this.screen = screen;
			this.controls = controls;
			this.frames();
		}

		private void frames() {
			// Four frames: a panel settles its scroll a frame after its content changes height.
			for (int i = 0; i < 4; i++) {
				ClickEverythingTest.this.clock.addAndGet(1_000_000_000L);
				FakeRenderer renderer = new FakeRenderer(960.0F, 540.0F);
				this.screen.render(renderer, this.mouseX, this.mouseY, 1.0F);
				assertTrue(renderer.balanced());
			}
		}

		@Override
		public List<Controls.Control> controls() {
			return this.controls.snapshot();
		}

		@Override
		public void click(float x, float y, MouseButton button) {
			this.mouseX = x;
			this.mouseY = y;
			this.frames();
			this.screen.mouseDown(x, y, button);
			this.screen.mouseUp(x, y, button);
			this.frames();
		}

		@Override
		public void escape() {
			this.screen.keyDown(Key.ESCAPE, false, false, false);
			this.frames();
		}

		@Override
		public void scroll(float x, float y, float amount) {
			this.mouseX = x;
			this.mouseY = y;
			this.frames();
			this.screen.mouseScroll(x, y, amount);
			this.frames();
		}

		@Override
		public String state(Controls.Control control) {
			return control.state().get();
		}
	}

	private static void assertPassed(ControlWalker.Result result, int atLeast) {
		assertTrue(result.passed(), "controls that did not respond: " + result.failures());
		assertTrue(result.tested().size() >= atLeast, "only reached " + result.tested().size() + " controls: " + result.tested());
	}

	@Test
	void everyClickGuiControlResponds() {
		FakePlatform platform = new FakePlatform(this.gameDirectory);
		new Client(platform);
		platform.press(Key.RIGHT_SHIFT, false);
		ClickGuiScreen screen = (ClickGuiScreen)platform.openScreen;
		ControlWalker.Result result = new ControlWalker(new Direct(screen, screen.controls())).walk("");
		// Panels, fold arrows, search, theme dots, every module row and arrow, and every setting control inside.
		assertPassed(result, 60);
		// Skipped, each with its reason: rows that swap this screen out, buttons that open other screens or send requests,
		// and the destructive ones (module reset, removing list entries), which have tests of their own.
		assertTrue(result.skipped().stream().allMatch(id -> id.startsWith("module:misc/clickgui") || id.startsWith("module:hud/hudeditor")
			|| id.startsWith("reset:") || id.startsWith("setting:client/")), "unexpected skips: " + result.skipped());
	}

	@Test
	void everyDesignScreenControlResponds() {
		FakePlatform platform = new FakePlatform(this.gameDirectory);
		Client client = new Client(platform);
		DesignScreen screen = new DesignScreen(client);
		ControlWalker.Result result = new ControlWalker(new Direct(screen, screen.controls())).walk("");
		assertPassed(result, 5);
	}
}
