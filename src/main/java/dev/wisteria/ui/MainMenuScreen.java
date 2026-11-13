package dev.wisteria.ui;

import dev.wisteria.Client;
import dev.wisteria.Wisteria;
import dev.wisteria.modules.hud.ProxyHud;
import dev.wisteria.platform.Key;
import dev.wisteria.platform.ScreenBackend;
import dev.wisteria.render.Renderer2D;
import dev.wisteria.ui.Metrics.Type;
import dev.wisteria.ui.gui.Controls;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The game's main menu in the client's own look: the mark, the name, and the game's own choices as design-system
 * buttons over the game's panorama. Mouse or keyboard (arrows or Tab to move, Enter to choose). The proxy line
 * says how the next connection will go before you pick a server.
 */
public final class MainMenuScreen implements ScreenBackend.ClientScreen {
	private static final float BUTTON_WIDTH = 168.0F;
	private static final float BUTTON_HEIGHT = 22.0F;
	private static final float GAP = 6.0F;
	private static final float STEP = 9.0F;

	/** What a button does. */
	public enum Choice {
		SINGLEPLAYER("menu.singleplayer", "Singleplayer"),
		MULTIPLAYER("menu.multiplayer", "Multiplayer"),
		OPTIONS("menu.options", "Options"),
		WISTERIA("", Wisteria.NAME),
		QUIT("menu.quit", "Quit Game");

		final String key;
		final String fallback;

		Choice(String key, String fallback) {
			this.key = key;
			this.fallback = fallback;
		}
	}

	private record Button(Choice choice, String label, float x, float y) {
	}

	private final Client client;
	private final Controls controls = new Controls();
	private final List<Button> buttons = new ArrayList<>();
	private final Animation[] hover = new Animation[Choice.values().length];
	private final Animation appear = new Animation(0.0F, Animation.SLOW, Easing.OUT_CUBIC);
	private final int[] presses = new int[Choice.values().length];
	private int focused = -1;
	private float creditsX;
	private float creditsWidth;
	private float height;

	public MainMenuScreen(Client client) {
		this.client = client;
		for (int i = 0; i < this.hover.length; i++) {
			this.hover[i] = new Animation(0.0F, Animation.FAST, Easing.OUT_CUBIC);
		}
	}

	@Override
	public String title() {
		return Wisteria.NAME;
	}

	@Override
	public boolean blurBackground() {
		return false;
	}

	public Controls controls() {
		return this.controls;
	}

	@Override
	public void render(Renderer2D renderer, float mouseX, float mouseY, float tickDelta) {
		Theme theme = this.client.themes().current();
		float width = renderer.width();
		this.height = renderer.height();
		this.appear.animateTo(1.0F);
		float shown = this.appear.value();
		this.controls.begin(mouseX, mouseY);

		// The panorama shows through a veil that darkens towards the left, where the menu sits.
		renderer.gradient(0.0F, 0.0F, width, this.height, Colors.withAlpha(theme.background(), 0xE0), Colors.withAlpha(theme.background(), 0x60),
			Colors.withAlpha(theme.background(), 0xF0), Colors.withAlpha(theme.background(), 0x80));

		float column = Math.max(24.0F, width * 0.12F);
		// On a short screen the buttons sit closer, so everything fits between the top and the footer.
		float gap = this.height < 300.0F ? 3.0F : GAP;
		float header = this.height < 300.0F ? 62.0F : 72.0F;
		float total = header + Choice.values().length * (BUTTON_HEIGHT + gap);
		float top = Math.max(10.0F, Math.min((this.height - total) / 2.0F, this.height - 22.0F - total)) + (1.0F - shown) * 10.0F;
		BrandMark.draw(renderer, column, top, STEP, theme, shown, null);
		String name = Wisteria.NAME.toUpperCase(Locale.ROOT);
		float textX = column + BrandMark.width(STEP) + 14.0F;
		float letterX = textX;
		for (int i = 0; i < name.length(); i++) {
			String letter = name.substring(i, i + 1);
			Type.TITLE.draw(renderer, letter, letterX, top + 14.0F, Colors.fade(theme.text(), shown));
			letterX += Type.TITLE.width(renderer, letter) + 4.0F;
		}

		Type.MONO.draw(renderer, "minecraft " + this.client.platform().minecraftVersion(), textX, top + 34.0F, Colors.fade(theme.textMuted(), shown));

		this.buttons.clear();
		float y = top + header;
		ScreenBackend screens = this.client.platform().screens();
		for (Choice choice : Choice.values()) {
			String label = choice.key.isEmpty() ? choice.fallback : screens.translate(choice.key, choice.fallback);
			this.buttons.add(new Button(choice, label, column, y));
			y += BUTTON_HEIGHT + gap;
		}

		for (int i = 0; i < this.buttons.size(); i++) {
			this.button(renderer, theme, i, shown);
		}

		// Where the next connection goes, before choosing a server.
		String[] proxy = ProxyHud.describe(this.client.proxies().route());
		int dot = switch (proxy[1]) {
			case "2" -> theme.success();
			case "1" -> theme.danger();
			default -> theme.warning();
		};
		float chipWidth = Type.CAPTION.width(renderer, proxy[0]) + 22.0F;
		float chipX = width - chipWidth - 8.0F;
		renderer.roundedRect(chipX, 8.0F, chipWidth, 15.0F, 7.5F, Colors.fade(Colors.withAlpha(theme.surface(), 0xD0), shown));
		renderer.roundedRect(chipX + 7.0F, 13.0F, 5.0F, 5.0F, 2.5F, Colors.fade(dot, shown));
		Type.CAPTION.draw(renderer, proxy[0], chipX + 16.0F, 9.5F, Colors.fade(theme.textMuted(), shown));

		String version = "Wisteria " + this.client.platform().clientVersion() + "  ·  Minecraft " + this.client.platform().minecraftVersion();
		Type.CAPTION.draw(renderer, version, 6.0F, this.height - 14.0F, Colors.fade(theme.textMuted(), shown));
		String credits = screens.translate("title.credits", "Copyright Mojang AB. Do not distribute!");
		this.creditsWidth = Type.CAPTION.width(renderer, credits);
		this.creditsX = width - this.creditsWidth - 6.0F;
		Controls.State link = this.controls.register("menu:credits", Controls.Kind.BUTTON, this.creditsX, this.height - 16.0F, this.creditsWidth, 14.0F,
			true, () -> "", "opens the game's credits screen; covered by its own test");
		Type.CAPTION.draw(renderer, credits, this.creditsX, this.height - 14.0F, Colors.fade(link.hovered() ? theme.text() : theme.textMuted(), shown));
		this.controls.end();
	}

	private void button(Renderer2D renderer, Theme theme, int index, float shown) {
		Button button = this.buttons.get(index);
		Controls.State state = this.controls.register("menu:" + button.choice().name().toLowerCase(Locale.ROOT), Controls.Kind.BUTTON,
			button.x(), button.y(), BUTTON_WIDTH, BUTTON_HEIGHT, true, () -> Integer.toString(this.presses[button.choice().ordinal()]),
			"opens another screen or leaves the game; covered by its own test");
		boolean lit = state.hovered() || index == this.focused;
		this.hover[index].animateTo(lit);
		float h = this.hover[index].value();
		boolean primary = button.choice() == Choice.SINGLEPLAYER || button.choice() == Choice.MULTIPLAYER;
		int base = primary ? Colors.mix(theme.raised(), theme.accent(), 0.18F + 0.82F * h) : Colors.mix(theme.raised(), theme.hover(), h);
		if (state.pressed()) {
			base = primary ? theme.accentPressed() : Colors.darken(theme.hover(), 0.2F);
		}

		float slide = h * 4.0F;
		renderer.roundedRect(button.x() + slide, button.y(), BUTTON_WIDTH, BUTTON_HEIGHT, Metrics.Radius.L, Colors.fade(Colors.withAlpha(base, 0xF0), shown));
		renderer.roundedOutline(button.x() + slide, button.y(), BUTTON_WIDTH, BUTTON_HEIGHT, Metrics.Radius.L, Metrics.Stroke.HAIRLINE,
			Colors.fade(Colors.withAlpha(lit ? theme.accent() : theme.outline(), 0xC0), shown));
		int text = primary && h > 0.5F ? theme.onAccent() : theme.text();
		Type.LABEL.draw(renderer, button.label(), button.x() + 14.0F + slide, button.y() + (BUTTON_HEIGHT - Type.LABEL.lineHeight(renderer)) / 2.0F,
			Colors.fade(text, shown));
	}

	/** Does what a button says; for the mouse, the keyboard and tests. */
	public void choose(Choice choice) {
		this.presses[choice.ordinal()]++;
		ScreenBackend screens = this.client.platform().screens();
		switch (choice) {
			case SINGLEPLAYER -> screens.openGame(ScreenBackend.GameScreen.SINGLEPLAYER);
			case MULTIPLAYER -> screens.openGame(ScreenBackend.GameScreen.MULTIPLAYER);
			case OPTIONS -> screens.openGame(ScreenBackend.GameScreen.OPTIONS);
			case QUIT -> screens.openGame(ScreenBackend.GameScreen.QUIT);
			case WISTERIA -> {
				var clickGui = this.client.modules().get("clickgui");
				if (clickGui != null) {
					this.client.setEnabled(clickGui, true);
				}
			}
		}
	}

	@Override
	public boolean mouseDown(float x, float y, ScreenBackend.MouseButton button) {
		if (button != ScreenBackend.MouseButton.LEFT) {
			return false;
		}

		for (Button candidate : this.buttons) {
			if (x >= candidate.x() && x < candidate.x() + BUTTON_WIDTH + 4.0F && y >= candidate.y() && y < candidate.y() + BUTTON_HEIGHT) {
				this.choose(candidate.choice());
				return true;
			}
		}

		if (x >= this.creditsX && x < this.creditsX + this.creditsWidth && y >= this.height - 16.0F) {
			this.client.platform().screens().openGame(ScreenBackend.GameScreen.CREDITS);
			return true;
		}

		return false;
	}

	@Override
	public void mouseUp(float x, float y, ScreenBackend.MouseButton button) {
		this.controls.release();
	}

	@Override
	public boolean keyDown(Key key, boolean shift, boolean control, boolean alt) {
		int count = Choice.values().length;
		switch (key) {
			case DOWN, TAB -> this.focused = shift && key == Key.TAB ? Math.floorMod(this.focused - 1, count) : Math.floorMod(this.focused + 1, count);
			case UP -> this.focused = Math.floorMod(this.focused - 1, count);
			case ENTER, NUMPAD_ENTER, SPACE -> {
				if (this.focused >= 0) {
					this.choose(Choice.values()[this.focused]);
				}
			}
			// The main menu has nowhere to go back to.
			case ESCAPE -> {
			}
			default -> {
				return false;
			}
		}

		return true;
	}

	/** The button the keyboard is on, or -1. */
	public int focused() {
		return this.focused;
	}
}
