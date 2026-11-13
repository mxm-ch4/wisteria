package dev.wisteria.platform.game;

import dev.wisteria.platform.Key;
import dev.wisteria.platform.ScreenBackend.ClientScreen;
import dev.wisteria.platform.ScreenBackend.MouseButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * The game's title screen with the client's menu drawn over the game's panorama. Still a {@link TitleScreen}, so the
 * game and everything that waits for "the title screen" treat it as one; none of the game's own buttons are added.
 */
public final class BrandedTitleScreen extends TitleScreen {
	private final GamePlatform platform;
	private final ClientScreen menu;

	BrandedTitleScreen(GamePlatform platform, ClientScreen menu) {
		super(false);
		this.platform = platform;
		this.menu = menu;
	}

	public ClientScreen menu() {
		return this.menu;
	}

	@Override
	protected void init() {
		// The client's buttons are drawn by the menu; the game's are left out.
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		this.extractPanorama(graphics, a);
		GuiRenderer2D renderer = new GuiRenderer2D(this.platform, graphics);
		try {
			this.menu.render(renderer, mouseX, mouseY, a);
		} finally {
			renderer.finish();
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		MouseButton button = GameScreens.button(event.buttonInfo().button());
		return button != null && this.menu.mouseDown((float)event.x(), (float)event.y(), button);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		MouseButton button = GameScreens.button(event.buttonInfo().button());
		if (button != null) {
			this.menu.mouseUp((float)event.x(), (float)event.y(), button);
		}

		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		return this.menu.mouseScroll((float)x, (float)y, (float)scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		Key key = KeyTable.fromNative(event.key());
		return key != null && this.menu.keyDown(key, event.hasShiftDown(), event.hasControlDown(), event.hasAltDown());
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		this.menu.charTyped(event.codepointAsString());
		return true;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}
}
