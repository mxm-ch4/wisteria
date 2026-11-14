package dev.wisteria.platform.game;

import com.mojang.blaze3d.platform.InputConstants;
import dev.wisteria.platform.InputBackend;
import dev.wisteria.platform.Key;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

public final class GameInput implements InputBackend {
	private final GamePlatform platform;
	private final List<InputListener> listeners = new CopyOnWriteArrayList<>();

	GameInput(GamePlatform platform) {
		this.platform = platform;
	}

	@Override
	public void releaseAll() {
		net.minecraft.client.KeyMapping.releaseAll();
	}

	@Override
	public boolean isDown(Key key) {
		Integer code = KeyTable.toNative(key);
		return code != null && this.platform.isNativeKeyDown(code);
	}

	@Override
	public void listen(InputListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public String clipboard() {
		return Minecraft.getInstance().keyboardHandler.getClipboard();
	}

	@Override
	public void setClipboard(String text) {
		Minecraft.getInstance().keyboardHandler.setClipboard(text);
	}

	/** Called by the keyboard mixin for every key event the game receives. */
	public void onNativeKey(int nativeAction, KeyEvent event) {
		if (this.listeners.isEmpty()) {
			return;
		}

		Key key = KeyTable.fromNative(event.key());
		if (key == null) {
			return;
		}

		Action action;
		if (nativeAction == InputConstants.PRESS) {
			action = Action.PRESS;
		} else if (nativeAction == InputConstants.RELEASE) {
			action = Action.RELEASE;
		} else if (nativeAction == InputConstants.REPEAT) {
			action = Action.REPEAT;
		} else {
			return;
		}

		boolean inScreen = this.platform.currentScreen() != null;
		for (InputListener listener : this.listeners) {
			listener.onKey(key, action, event.hasShiftDown(), event.hasControlDown(), event.hasAltDown(), inScreen);
		}
	}
}
