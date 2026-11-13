package dev.wisteria.compat.common;

import dev.wisteria.compat.ScreenBackend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class CommonScreens implements ScreenBackend {
	private final CommonPlatform platform;
	private volatile @Nullable ClientScreen pending;

	CommonScreens(CommonPlatform platform) {
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

	@Override
	public void close() {
		this.pending = null;
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

	/** The game screen that hosts a client screen. Input forwarding comes with the GUI stage. */
	private static final class Host extends Screen {
		private final CommonPlatform platform;
		private final ClientScreen screen;

		Host(CommonPlatform platform, ClientScreen screen) {
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
		public void removed() {
			super.removed();
			this.screen.onClose();
		}

		@Override
		public boolean isPauseScreen() {
			return false;
		}
	}
}
