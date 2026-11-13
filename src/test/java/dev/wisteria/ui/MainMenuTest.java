package dev.wisteria.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wisteria.Client;
import dev.wisteria.FakePlatform;
import dev.wisteria.FakeRenderer;
import dev.wisteria.platform.Key;
import dev.wisteria.platform.ScreenBackend;
import dev.wisteria.platform.ScreenBackend.MouseButton;
import dev.wisteria.ui.gui.Controls;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainMenuTest {
	@TempDir
	Path game;

	private final AtomicLong clock = new AtomicLong(1_000_000_000L);
	private FakePlatform platform;
	private Client client;
	private MainMenuScreen menu;
	private FakeRenderer frame;

	@BeforeEach
	void start() {
		Animation.useClock(this.clock::get);
		this.platform = new FakePlatform(this.game);
		this.client = new Client(this.platform);
		this.menu = assertInstanceOf(MainMenuScreen.class, this.platform.mainMenu.get());
		this.render();
	}

	@AfterEach
	void realClock() {
		Animation.useRealClock();
	}

	private void render() {
		for (int i = 0; i < 2; i++) {
			this.clock.addAndGet(1_000_000_000L);
			this.frame = new FakeRenderer(960.0F, 540.0F);
			this.menu.render(this.frame, 0.0F, 0.0F, 1.0F);
			assertTrue(this.frame.balanced());
		}
	}

	private void click(String id) {
		Controls.Control control = this.menu.controls().snapshot().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
		assertTrue(this.menu.mouseDown(control.centerX(), control.centerY(), MouseButton.LEFT), id);
		this.menu.mouseUp(control.centerX(), control.centerY(), MouseButton.LEFT);
	}

	@Test
	void showsTheBrandTheChoicesAndTheConnectionRoute() {
		assertTrue(this.frame.drew("W") && this.frame.drew("Singleplayer") && this.frame.drew("Multiplayer") && this.frame.drew("Options")
			&& this.frame.drew("Wisteria") && this.frame.drew("Quit Game"));
		assertTrue(this.frame.drew("Proxy off: direct connection"), "where the next connection goes");
		assertTrue(this.frame.drew("Copyright Mojang AB. Do not distribute!"), "the game's credits line stays");
		this.platform.typeInChat(".toggle proxy on");
		this.render();
		assertTrue(this.frame.drew("Proxy: blocked, nothing connects"));
	}

	@Test
	void eachButtonOpensItsScreen() {
		this.click("menu:singleplayer");
		this.click("menu:multiplayer");
		this.click("menu:options");
		this.click("menu:credits");
		this.click("menu:quit");
		assertEquals(List.of(ScreenBackend.GameScreen.SINGLEPLAYER, ScreenBackend.GameScreen.MULTIPLAYER, ScreenBackend.GameScreen.OPTIONS,
			ScreenBackend.GameScreen.CREDITS, ScreenBackend.GameScreen.QUIT), this.platform.gameScreens);
		this.click("menu:wisteria");
		assertTrue(this.client.modules().get("clickgui").isEnabled(), "the client's own button opens the ClickGUI");
	}

	@Test
	void worksFromTheKeyboard() {
		assertEquals(-1, this.menu.focused());
		this.menu.keyDown(Key.DOWN, false, false, false);
		this.menu.keyDown(Key.DOWN, false, false, false);
		assertEquals(1, this.menu.focused());
		this.menu.keyDown(Key.ENTER, false, false, false);
		assertEquals(List.of(ScreenBackend.GameScreen.MULTIPLAYER), this.platform.gameScreens);
		this.menu.keyDown(Key.UP, false, false, false);
		this.menu.keyDown(Key.UP, false, false, false);
		assertEquals(4, this.menu.focused(), "wraps around");
		assertTrue(this.menu.keyDown(Key.ESCAPE, false, false, false), "Escape is kept: the main menu has nowhere to go back to");
	}

	@Test
	void canBeSwitchedOff() {
		this.platform.typeInChat(".interface branded-menu off");
		assertNull(this.platform.mainMenu.get(), "the game's own title screen stays");
		this.platform.typeInChat(".interface branded-menu on");
		assertInstanceOf(MainMenuScreen.class, this.platform.mainMenu.get());
	}
}
