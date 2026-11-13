package dev.wisteria.platform.game;

import com.mojang.blaze3d.platform.InputConstants;
import dev.wisteria.platform.Key;
import dev.wisteria.platform.ScreenBackend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class GameScreens implements ScreenBackend {
	private final GamePlatform platform;
	private volatile @Nullable ClientScreen pending;

	GameScreens(GamePlatform platform) {
		this.platform = platform;
	}

	// Deferred to the next tick: a screen opened from a chat command would otherwise be closed at once
	// by the chat box, which closes "the current screen" right after sending.
	@Override
	public void open(ClientScreen screen) {
		this.pending = screen;
	}

	/** Called at the end of every client tick. */
	void tick() {
		ClientScreen screen = this.pending;
		if (screen != null) {
			this.pending = null;
			this.platform.setScreen(new Host(this.platform, screen));
		}
	}

	/**
	 * True while a client screen is being taken down by the game (Escape, another screen replacing it): its
	 * onClose often switches its module off, which asks to close the very screen that is already going.
	 */
	private static boolean removing;

	@Override
	public void close() {
		this.pending = null;
		if (removing) {
			return;
		}

		Minecraft.getInstance().execute(() -> {
			if (this.platform.currentScreen() instanceof Host) {
				this.platform.setScreen(null);
			}
		});
	}

	@Override
	public boolean isAnyOpen() {
		return this.pending != null || this.platform.currentScreen() != null;
	}

	/** The game screen that hosts a client screen and passes it the game's input. */
	private static final class Host extends Screen {
		private final GamePlatform platform;
		private final ClientScreen screen;

		Host(GamePlatform platform, ClientScreen screen) {
			super(Component.literal(screen.title()));
			this.platform = platform;
			this.screen = screen;
		}

		@Override
		public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			if (this.screen.blurBackground()) {
				super.extractBackground(graphics, mouseX, mouseY, a);
			}
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			super.extractRenderState(graphics, mouseX, mouseY, a);
			GuiRenderer2D renderer = new GuiRenderer2D(this.platform, graphics);
			try {
				this.screen.render(renderer, mouseX, mouseY, a);
			} finally {
				renderer.finish();
			}
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			MouseButton button = button(event.buttonInfo().button());
			return button != null && this.screen.mouseDown((float)event.x(), (float)event.y(), button) || super.mouseClicked(event, doubleClick);
		}

		@Override
		public boolean mouseReleased(MouseButtonEvent event) {
			MouseButton button = button(event.buttonInfo().button());
			if (button != null) {
				this.screen.mouseUp((float)event.x(), (float)event.y(), button);
			}

			return super.mouseReleased(event);
		}

		@Override
		public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
			return this.screen.mouseScroll((float)x, (float)y, (float)scrollY) || super.mouseScrolled(x, y, scrollX, scrollY);
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			Key key = KeyTable.fromNative(event.key());
			// Offered to the client screen first; what it does not use goes on to the game, which is how Escape closes.
			return key != null && this.screen.keyDown(key, event.hasShiftDown(), event.hasControlDown(), event.hasAltDown()) || super.keyPressed(event);
		}

		@Override
		public boolean charTyped(CharacterEvent event) {
			this.screen.charTyped(event.codepointAsString());
			return true;
		}

		// Button numbers differ between versions (GLFW counts from 0, SDL from 1); the constant names do not.
		private static @Nullable MouseButton button(int nativeButton) {
			if (nativeButton == InputConstants.MOUSE_BUTTON_LEFT) {
				return MouseButton.LEFT;
			}

			if (nativeButton == InputConstants.MOUSE_BUTTON_RIGHT) {
				return MouseButton.RIGHT;
			}

			return nativeButton == InputConstants.MOUSE_BUTTON_MIDDLE ? MouseButton.MIDDLE : null;
		}

		@Override
		public void removed() {
			super.removed();
			removing = true;
			try {
				this.screen.onClose();
			} finally {
				removing = false;
			}
		}

		@Override
		public boolean isPauseScreen() {
			return false;
		}
	}
}
