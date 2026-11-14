package dev.wisteria.ui.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wisteria.Client;
import dev.wisteria.FakePlatform;
import dev.wisteria.FakeRenderer;
import dev.wisteria.platform.Key;
import dev.wisteria.platform.ScreenBackend.MouseButton;
import dev.wisteria.module.Category;
import dev.wisteria.module.Module;
import dev.wisteria.modules.clickgui.ClickGui;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives the ClickGUI the way a player would: frames, clicks, drags and keys. Panels start at (8, 32) and
 * are 118 wide with a 22 px header; module rows are 17 px. Render lists FullBright and ESP; Watermark is in the HUD panel.
 */
class ClickGuiTest {
	private static final float RENDER_X = 8.0F;
	private static final float RENDER_Y = 32.0F;
	private static final float FIRST_ROW = RENDER_Y + 22.0F + 1.0F;

	@TempDir
	Path gameDirectory;

	private FakePlatform platform;
	private Client client;
	private FakeRenderer renderer;
	private ClickGuiScreen screen;
	private float mouseX;
	private float mouseY;

	@BeforeEach
	void open() throws InterruptedException {
		this.platform = new FakePlatform(this.gameDirectory);
		this.client = new Client(this.platform);
		this.platform.press(Key.RIGHT_SHIFT, false);
		this.screen = (ClickGuiScreen)this.platform.openScreen;
		assertNotNull(this.screen, "Right Shift opens the GUI");
		// The screen fades in; text is not drawn until it is visible, so let the opening animation finish.
		this.frame();
		Thread.sleep(380L);
		this.frame();
	}

	private void frame() {
		this.renderer = new FakeRenderer(640.0F, 360.0F);
		this.screen.render(this.renderer, this.mouseX, this.mouseY, 1.0F);
		assertTrue(this.renderer.balanced(), "every scissor and transform pushed in a frame is popped");
	}

	/** Lets animations finish, then draws a frame so the layout is up to date. */
	private void settle() throws InterruptedException {
		this.frame();
		Thread.sleep(260L);
		this.frame();
	}

	private void move(float x, float y) {
		this.mouseX = x;
		this.mouseY = y;
		this.frame();
	}

	private void click(float x, float y, MouseButton button) {
		this.move(x, y);
		this.screen.mouseDown(x, y, button);
		this.screen.mouseUp(x, y, button);
		this.frame();
	}

	private Module module(String name) {
		return this.client.modules().get(name);
	}

	/** Y of the n-th module row of the Render panel, in its middle. */
	private static float row(int index) {
		return FIRST_ROW + index * ModuleRow.HEIGHT + ModuleRow.HEIGHT / 2.0F;
	}

	@Test
	void showsEveryCategoryThatHasModules() {
		assertEquals(5, this.screen.panels().size());
		assertTrue(this.renderer.drew("Render") && this.renderer.drew("World") && this.renderer.drew("Misc") && this.renderer.drew("HUD") && this.renderer.drew("Client"));
		assertTrue(this.renderer.drew("FullBright") && this.renderer.drew("ESP") && this.renderer.drew("Watermark") && this.renderer.drew("ClickGUI"));
		assertFalse(this.renderer.drew("Combat"), "empty categories get no panel");
	}

	@Test
	void leftClickTogglesAModule() {
		assertFalse(this.module("esp").isEnabled());
		this.click(RENDER_X + 40.0F, row(1), MouseButton.LEFT);
		assertTrue(this.module("esp").isEnabled());
		this.click(RENDER_X + 40.0F, row(1), MouseButton.LEFT);
		assertFalse(this.module("esp").isEnabled());
		assertFalse(this.module("fullbright").isEnabled());
	}

	@Test
	void rightClickUnfoldsSettingsAndTheToggleWorks() throws InterruptedException {
		this.click(RENDER_X + 40.0F, row(1), MouseButton.RIGHT);
		this.settle();
		assertTrue(this.renderer.drew("Range") && this.renderer.drew("Players") && this.renderer.drew("Bind"));

		// Rows under ESP: 4 px padding, Bind (15), Range slider (24), then Players (15).
		float players = FIRST_ROW + 2.0F * ModuleRow.HEIGHT + 4.0F + 15.0F + 24.0F + 7.0F;
		assertEquals("on", this.module("esp").setting("players").format());
		this.click(RENDER_X + 60.0F, players, MouseButton.LEFT);
		assertEquals("off", this.module("esp").setting("players").format());
		assertFalse(this.module("esp").isEnabled(), "clicking a setting does not toggle the module");
	}

	@Test
	void draggingASliderSetsTheValue() throws InterruptedException {
		this.click(RENDER_X + 40.0F, row(1), MouseButton.RIGHT);
		this.settle();
		float slider = FIRST_ROW + 2.0F * ModuleRow.HEIGHT + 4.0F + 15.0F + 16.0F;
		float left = RENDER_X + 9.0F;
		float width = 118.0F - 18.0F;

		this.move(left + width * 0.5F, slider);
		this.screen.mouseDown(left + width * 0.5F, slider, MouseButton.LEFT);
		assertEquals("132", this.module("esp").setting("range").format(), "half of 8..256");
		this.move(left + width * 2.0F, slider);
		assertEquals("256", this.module("esp").setting("range").format(), "dragging past the end clamps");
		this.move(left - 50.0F, slider + 80.0F);
		assertEquals("8", this.module("esp").setting("range").format(), "the drag follows the cursor even off the track");
		this.screen.mouseUp(left, slider, MouseButton.LEFT);
		this.move(left + width, slider);
		assertEquals("8", this.module("esp").setting("range").format(), "released: moving the cursor changes nothing");

		this.click(left + 10.0F, slider, MouseButton.RIGHT);
		assertEquals("64", this.module("esp").setting("range").format(), "right-click resets to the default");
	}

	@Test
	void recordingABind() throws InterruptedException {
		this.click(RENDER_X + 40.0F, row(1), MouseButton.RIGHT);
		this.settle();
		float bind = FIRST_ROW + 2.0F * ModuleRow.HEIGHT + 4.0F + 7.0F;
		this.click(RENDER_X + 90.0F, bind, MouseButton.LEFT);
		assertTrue(this.renderer.drew("press a key"));

		assertTrue(this.screen.keyDown(Key.G, false, false, false));
		assertEquals(Optional.of(Key.G), this.module("esp").bind().get());
		assertNotNull(this.platform.openScreen, "the key went to the bind, not to closing the GUI");

		this.click(RENDER_X + 90.0F, bind, MouseButton.LEFT);
		assertTrue(this.screen.keyDown(Key.ESCAPE, false, false, false), "Escape is used up clearing the bind");
		assertEquals(Optional.empty(), this.module("esp").bind().get());
		assertFalse(this.screen.keyDown(Key.ESCAPE, false, false, false), "now Escape is free to close the screen");
	}

	@Test
	void typingSearchesAndFiltersModules() {
		this.screen.charTyped("f");
		this.screen.charTyped("u");
		this.frame();
		assertEquals("fu", this.screen.search().text());
		assertTrue(this.renderer.drew("FullBright"));
		assertFalse(this.renderer.drew("ESP") || this.renderer.drew("Watermark") || this.renderer.drew("ClickGUI"));

		// With one match, it is the first row of its panel.
		this.click(RENDER_X + 40.0F, row(0), MouseButton.LEFT);
		assertTrue(this.module("fullbright").isEnabled());

		this.screen.search().setFocused(true);
		this.screen.keyDown(Key.BACKSPACE, false, true, false);
		this.frame();
		assertEquals("", this.screen.search().text());
		assertTrue(this.renderer.drew("ESP"));
	}

	@Test
	void whileTypingTheGuiKeyIsJustAKey() {
		this.screen.charTyped("e");
		assertTrue(this.screen.keyDown(Key.RIGHT_SHIFT, true, false, false));
		assertNotNull(this.platform.openScreen, "shift while searching does not close the GUI");

		this.screen.keyDown(Key.ESCAPE, false, false, false);
		assertFalse(this.screen.search().isFocused());
		assertTrue(this.screen.keyDown(Key.RIGHT_SHIFT, false, false, false));
		assertNull(this.platform.openScreen, "with nothing focused the GUI key closes it");
		assertFalse(this.module("clickgui").isEnabled());
	}

	@Test
	void panelsCanBeDraggedFoldedAndAreRemembered() throws InterruptedException {
		Panel render = this.screen.panels().stream().filter(panel -> panel.category() == Category.RENDER).findFirst().orElseThrow();
		this.move(RENDER_X + 30.0F, RENDER_Y + 10.0F);
		this.screen.mouseDown(RENDER_X + 30.0F, RENDER_Y + 10.0F, MouseButton.LEFT);
		this.move(RENDER_X + 230.0F, RENDER_Y + 60.0F);
		this.screen.mouseUp(RENDER_X + 230.0F, RENDER_Y + 60.0F, MouseButton.LEFT);
		assertEquals(RENDER_X + 200.0F, render.x(), 0.01F);
		assertEquals(RENDER_Y + 50.0F, render.y(), 0.01F);

		this.click(render.x() + 30.0F, render.y() + 10.0F, MouseButton.RIGHT);
		assertTrue(render.isCollapsed());
		this.settle();
		this.click(render.x() + 30.0F, render.y() + 22.0F + 10.0F, MouseButton.LEFT);
		assertFalse(this.module("fullbright").isEnabled(), "a folded panel has no rows to click");

		this.platform.press(Key.RIGHT_SHIFT, true);
		this.screen.keyDown(Key.RIGHT_SHIFT, false, false, false);
		this.platform.stop();
		Client after = new Client(new FakePlatform(this.gameDirectory));
		GuiState.PanelState saved = after.gui().panel(Category.RENDER);
		assertNotNull(saved);
		assertEquals(RENDER_X + 200.0F, saved.x(), 0.01F);
		assertTrue(saved.collapsed());
	}

	@Test
	void themeDotsSwitchTheTheme() {
		// Five dots right-aligned on the status line: 640 - 10 - (5 - index) * 15.
		this.click(640.0F - 10.0F - 3.0F * 15.0F + 4.0F, 360.0F - 15.0F + 5.0F, MouseButton.LEFT);
		assertEquals("mint", this.client.themes().presetId());
	}

	@Test
	void theGuiModuleIsNeverRestoredAsOpen() {
		assertTrue(this.module("clickgui").isEnabled());
		this.platform.stop();
		FakePlatform next = new FakePlatform(this.gameDirectory);
		Client after = new Client(next);
		assertFalse(after.modules().get("clickgui").isEnabled());
		assertNull(next.openScreen);
		assertTrue(after.modules().get("clickgui").bind().matches(Key.RIGHT_SHIFT), "the default bind survives a profile load");
		assertTrue(after.modules().get("clickgui") instanceof ClickGui);
	}

	@Test
	void hoveringExplainsWhatIsUnderTheCursor() {
		this.move(RENDER_X + 40.0F, row(0));
		this.frame();
		assertTrue(this.renderer.drew(this.module("fullbright").description()));
	}

	@Test
	void switchingProfilesFromTheMenuKeepsItOpen() {
		this.client.config().create("other");
		this.client.modules().get("profiles").setting("active").parse("other");
		assertEquals("other", this.client.config().profile());
		assertTrue(this.module("clickgui").isEnabled(), "the ClickGUI the switch was made from is still open");
		assertNotNull(this.platform.openScreen);
	}
}
