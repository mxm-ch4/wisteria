package dev.wisteria.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wisteria.Client;
import dev.wisteria.FakePlatform;
import dev.wisteria.FakeRenderer;
import dev.wisteria.platform.Key;
import dev.wisteria.platform.ScreenBackend.MouseButton;
import dev.wisteria.ui.Animation;
import dev.wisteria.ui.gui.ControlWalker;
import dev.wisteria.ui.gui.Controls;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The HUD through the client: drawing, the editor with the mouse, saving, the .hud command, toasts. */
class HudTest {
	@TempDir
	Path game;

	private final AtomicLong clock = new AtomicLong(1_000_000_000L);
	private FakePlatform platform;
	private Client client;

	@BeforeEach
	void start() {
		Animation.useClock(this.clock::get);
		this.platform = new FakePlatform(this.game);
		this.client = new Client(this.platform);
	}

	@AfterEach
	void realClock() {
		Animation.useRealClock();
	}

	/** Two frames a second apart, so anything switched on has finished fading in; returns the second. */
	private FakeRenderer hudFrame() {
		FakeRenderer renderer = null;
		for (int i = 0; i < 2; i++) {
			this.clock.addAndGet(1_000_000_000L);
			renderer = new FakeRenderer(960.0F, 540.0F);
			this.platform.renderHud(renderer);
			assertTrue(renderer.balanced());
		}

		return renderer;
	}

	private HudEditorScreen openEditor() {
		this.platform.typeInChat(".hud");
		HudEditorScreen editor = (HudEditorScreen)this.platform.openScreen;
		assertNotNull(editor);
		this.editorFrame(editor, 0.0F, 0.0F);
		return editor;
	}

	private void editorFrame(HudEditorScreen editor, float mouseX, float mouseY) {
		this.clock.addAndGet(1_000_000_000L);
		FakeRenderer renderer = new FakeRenderer(960.0F, 540.0F);
		editor.render(renderer, mouseX, mouseY, 1.0F);
		assertTrue(renderer.balanced());
	}

	private Controls.Control control(HudEditorScreen editor, String id) {
		return editor.controls().snapshot().stream().filter(control -> control.id().equals(id)).findFirst().orElseThrow(() -> new AssertionError("no " + id));
	}

	@Test
	void enabledElementsAreDrawnWithTheirData() {
		this.platform.typeInChat(".toggle esp on");
		assertFalse(this.hudFrame().drew("ESP"), "the ArrayList is off");
		this.platform.typeInChat(".toggle arraylist on");
		this.platform.typeInChat(".toggle keybinds on");
		FakeRenderer frame = this.hudFrame();
		assertTrue(frame.drew("ESP"), "the ArrayList lists modules that are on");
		assertTrue(frame.drew("KEYBINDS") && frame.drew("RShift"), "the ClickGUI's Right Shift is listed");
		this.platform.typeInChat(".toggle coordinates on");
		assertFalse(this.hudFrame().drew("XYZ"), "no world, no coordinates outside the editor");
	}

	@Test
	void theEditorShowsSamplesAndPinsElementsWhereTheyAreDropped() {
		this.platform.typeInChat(".toggle coordinates on");
		HudEditorScreen editor = this.openEditor();
		assertTrue(this.client.hud().isEditing());
		Controls.Control box = this.control(editor, "hud:coordinates");
		assertEquals(Anchor.BOTTOM_LEFT, this.client.hud().placement(this.client.hud().element("coordinates")).anchor());

		// Drag it by its middle to the top right; it snaps to the right edge.
		float grabX = box.centerX();
		float grabY = box.centerY();
		editor.mouseDown(grabX, grabY, MouseButton.LEFT);
		float targetX = 960.0F - box.width() / 2.0F - 2.0F;
		float targetY = 120.0F;
		this.editorFrame(editor, targetX, targetY);
		editor.mouseUp(targetX, targetY, MouseButton.LEFT);
		this.editorFrame(editor, targetX, targetY);
		HudLayout.Placement moved = this.client.hud().placement(this.client.hud().element("coordinates"));
		assertEquals(Anchor.TOP_RIGHT, moved.anchor());
		assertEquals(0.0F, moved.dx(), "2 px from the edge snaps onto it");
		assertEquals(this.control(editor, "hud:coordinates").x() + box.width(), 960.0F, 0.01F);

		// Right click puts it back.
		Controls.Control now = this.control(editor, "hud:coordinates");
		editor.mouseDown(now.centerX(), now.centerY(), MouseButton.RIGHT);
		editor.mouseUp(now.centerX(), now.centerY(), MouseButton.RIGHT);
		assertFalse(this.client.hud().isMoved(this.client.hud().element("coordinates")));

		editor.keyDown(Key.ESCAPE, false, false, false);
		editor.onClose();
		assertFalse(this.client.hud().isEditing());
		assertFalse(this.client.modules().get("hudeditor").isEnabled());
	}

	@Test
	void arrowsNudgeTheSelectedElement() {
		this.platform.typeInChat(".toggle fps on");
		HudEditorScreen editor = this.openEditor();
		Controls.Control box = this.control(editor, "hud:fps");
		editor.mouseDown(box.centerX(), box.centerY(), MouseButton.LEFT);
		editor.mouseUp(box.centerX(), box.centerY(), MouseButton.LEFT);
		assertSame(this.client.hud().element("fps"), editor.selected());
		editor.keyDown(Key.RIGHT, false, false, false);
		editor.keyDown(Key.DOWN, true, false, false);
		this.editorFrame(editor, 0.0F, 0.0F);
		HudLayout.Placement placement = this.client.hud().placement(this.client.hud().element("fps"));
		assertEquals(7.0F, placement.dx());
		assertEquals(76.0F, placement.dy());
	}

	@Test
	void everyEditorControlResponds() {
		this.platform.typeInChat(".toggle watermark on");
		this.platform.typeInChat(".toggle coordinates on");
		this.platform.typeInChat(".hud coordinates center");
		HudEditorScreen editor = this.openEditor();
		ControlWalker.Result result = new ControlWalker(new ControlWalker.Driver() {
			private float mouseX;
			private float mouseY;

			@Override
			public List<Controls.Control> controls() {
				return editor.controls().snapshot();
			}

			@Override
			public void click(float x, float y, MouseButton button) {
				this.mouseX = x;
				this.mouseY = y;
				HudTest.this.editorFrame(editor, x, y);
				editor.mouseDown(x, y, button);
				editor.mouseUp(x, y, button);
				HudTest.this.editorFrame(editor, this.mouseX, this.mouseY);
			}

			@Override
			public void escape() {
				editor.keyDown(Key.ESCAPE, false, false, false);
			}

			@Override
			public String state(Controls.Control control) {
				return control.state().get();
			}
		}).walk("");
		assertTrue(result.passed(), "controls that did not respond: " + result.failures());
		// 11 element chips, Reset, and the two elements on screen.
		assertTrue(result.tested().size() >= 13, "reached " + result.tested());
	}

	@Test
	void placementsSurviveARestartAndTheCommandPlacesByName() {
		this.platform.typeInChat(".hud watermark bottom-right");
		this.platform.typeInChat(".hud fps center 10 -20");
		this.platform.typeInChat(".hud nothing top");
		assertTrue(this.platform.chatLines.getLast().contains("No HUD element"), this.platform.chatLines.getLast());
		this.platform.typeInChat(".hud fps middle");
		assertTrue(this.platform.chatLines.getLast().contains("not an anchor"), this.platform.chatLines.getLast());
		this.platform.stop();

		Client after = new Client(new FakePlatform(this.game));
		HudManager hud = after.hud();
		assertEquals(new HudLayout.Placement(Anchor.BOTTOM_RIGHT, -6.0F, -6.0F), hud.placement(hud.element("watermark")));
		assertEquals(new HudLayout.Placement(Anchor.CENTER, 10.0F, -20.0F), hud.placement(hud.element("fps")));
		hud.reset(hud.element("fps"));
		assertFalse(hud.isMoved(hud.element("fps")));
	}

	@Test
	void togglingModulesRaisesToasts() {
		this.platform.typeInChat(".toggle notifications on");
		this.platform.tick(1);
		assertNull(this.firstToast(), "nothing at startup");
		this.platform.typeInChat(".toggle esp on");
		this.platform.tick(1);
		assertTrue(this.hudFrame().drew("ESP") && this.hudFrame().drew("Enabled"));
		this.platform.typeInChat(".toggle esp off");
		this.platform.tick(1);
		assertEquals(List.of("ESP Enabled", "ESP Disabled"),
			this.client.hud().toasts().shown().stream().map(shown -> shown.toast().title() + " " + shown.toast().text()).toList());
		this.clock.addAndGet(Toasts.SHOW_NANOS);
	}

	private Toasts.Toast firstToast() {
		List<Toasts.Shown> shown = this.client.hud().toasts().shown();
		return shown.isEmpty() ? null : shown.getFirst().toast();
	}

	@Test
	void cornerHandlesAndTheWheelResizeAndTheSizeIsSaved() {
		this.platform.typeInChat(".toggle fps on");
		this.platform.typeInChat(".toggle potions on");
		HudEditorScreen editor = this.openEditor();
		HudElement fps = this.client.hud().element("fps");
		Controls.Control box = this.control(editor, "hud:fps");

		// Select it, then pull its bottom-right handle until it is twice as wide.
		editor.mouseDown(box.centerX(), box.centerY(), MouseButton.LEFT);
		editor.mouseUp(box.centerX(), box.centerY(), MouseButton.LEFT);
		this.editorFrame(editor, box.centerX(), box.centerY());
		float handleX = box.x() + box.width();
		float handleY = box.y() + box.height();
		assertTrue(editor.mouseDown(handleX, handleY, MouseButton.LEFT));
		this.editorFrame(editor, box.x() + box.width() * 2.0F, box.y() + box.height() * 2.0F);
		editor.mouseUp(box.x() + box.width() * 2.0F, box.y() + box.height() * 2.0F, MouseButton.LEFT);
		assertEquals(2.0F, fps.scale(), 0.001F);
		Controls.Control bigger = this.control(editor, "hud:fps");
		this.editorFrame(editor, 0.0F, 0.0F);
		assertEquals(box.x(), this.control(editor, "hud:fps").x(), 0.01F, "pinned top left: it grew to the right");
		assertEquals(box.width() * 2.0F, bigger.width(), 0.5F);

		// The wheel over another element: one notch is 0.05.
		Controls.Control potions = this.control(editor, "hud:potions");
		assertTrue(editor.mouseScroll(potions.centerX(), potions.centerY(), 1.0F));
		assertEquals(1.05F, this.client.hud().element("potions").scale(), 0.001F);

		// Reset size: for the selected element.
		Controls.Control reset = this.control(editor, "hud-reset-size");
		assertTrue(reset.enabled());
		editor.mouseDown(reset.centerX(), reset.centerY(), MouseButton.LEFT);
		editor.mouseUp(reset.centerX(), reset.centerY(), MouseButton.LEFT);
		assertEquals(1.0F, this.client.hud().element("potions").scale());

		this.platform.typeInChat(".hud fps scale 2.5");
		this.platform.typeInChat(".hudeditor hud-scale 1.5");
		this.platform.stop();
		Client after = new Client(new FakePlatform(this.game));
		assertEquals(2.5F, after.hud().element("fps").scale());
		assertEquals(1.5F, after.hud().globalScale());
		this.platform.typeInChat(".hud fps scale reset");
		assertEquals(1.0F, fps.scale());
	}

	@Test
	void theHudWideSizeMultipliesEachElement() {
		this.platform.typeInChat(".toggle keybinds on");
		this.hudFrame();
		float width = this.client.hud().lastDrawn().getFirst().rect().width();
		this.platform.typeInChat(".hudeditor hud-scale 2");
		this.platform.typeInChat(".keybinds scale 1.5");
		this.hudFrame();
		assertEquals(width * 3.0F, this.client.hud().lastDrawn().getFirst().rect().width(), 0.01F);
		this.platform.typeInChat(".hudeditor hud-scale 5");
		assertEquals(2.0F, this.client.hud().globalScale(), "kept in range");
	}

	@Test
	void theHelpLineKeepsClearOfElementsAndTheHotbar() {
		for (String id : List.of("potions", "notifications", "fps", "watermark")) {
			this.platform.typeInChat(".toggle " + id + " on");
		}

		// Put one element right under the toolbar, where the help line goes first.
		this.platform.typeInChat(".hud fps top 0 60");
		HudEditorScreen editor = this.openEditor();
		this.editorFrame(editor, 0.0F, 0.0F);
		HudLayout.Rect hint = editor.hintBox();
		assertNotNull(hint);
		for (HudManager.Drawn item : this.client.hud().lastDrawn()) {
			assertFalse(HudEditorScreen.overlaps(item.rect(), hint), "the help line covers " + item.element().id() + ": " + hint + " vs " + item.rect());
		}

		assertTrue(hint.bottom() <= 540.0F - 40.0F, "never on the hotbar: " + hint);
	}
}
