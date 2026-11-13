package dev.wisteria.compat.common;

import com.mojang.blaze3d.platform.InputConstants;
import dev.wisteria.compat.Key;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Native key code to {@link Key} and back. The numbers differ between versions (GLFW key codes up to 26.2,
 * SDL scancodes from 26.3), but the game's constant names do not, and this file is compiled once per version.
 */
final class KeyTable {
	private static final Map<Integer, Key> BY_CODE = new HashMap<>();
	private static final Map<Key, Integer> BY_KEY = new EnumMap<>(Key.class);

	static {
		put(Key.A, InputConstants.KEY_A); put(Key.B, InputConstants.KEY_B); put(Key.C, InputConstants.KEY_C);
		put(Key.D, InputConstants.KEY_D); put(Key.E, InputConstants.KEY_E); put(Key.F, InputConstants.KEY_F);
		put(Key.G, InputConstants.KEY_G); put(Key.H, InputConstants.KEY_H); put(Key.I, InputConstants.KEY_I);
		put(Key.J, InputConstants.KEY_J); put(Key.K, InputConstants.KEY_K); put(Key.L, InputConstants.KEY_L);
		put(Key.M, InputConstants.KEY_M); put(Key.N, InputConstants.KEY_N); put(Key.O, InputConstants.KEY_O);
		put(Key.P, InputConstants.KEY_P); put(Key.Q, InputConstants.KEY_Q); put(Key.R, InputConstants.KEY_R);
		put(Key.S, InputConstants.KEY_S); put(Key.T, InputConstants.KEY_T); put(Key.U, InputConstants.KEY_U);
		put(Key.V, InputConstants.KEY_V); put(Key.W, InputConstants.KEY_W); put(Key.X, InputConstants.KEY_X);
		put(Key.Y, InputConstants.KEY_Y); put(Key.Z, InputConstants.KEY_Z);

		put(Key.NUM_0, InputConstants.KEY_0); put(Key.NUM_1, InputConstants.KEY_1); put(Key.NUM_2, InputConstants.KEY_2);
		put(Key.NUM_3, InputConstants.KEY_3); put(Key.NUM_4, InputConstants.KEY_4); put(Key.NUM_5, InputConstants.KEY_5);
		put(Key.NUM_6, InputConstants.KEY_6); put(Key.NUM_7, InputConstants.KEY_7); put(Key.NUM_8, InputConstants.KEY_8);
		put(Key.NUM_9, InputConstants.KEY_9);

		put(Key.F1, InputConstants.KEY_F1); put(Key.F2, InputConstants.KEY_F2); put(Key.F3, InputConstants.KEY_F3);
		put(Key.F4, InputConstants.KEY_F4); put(Key.F5, InputConstants.KEY_F5); put(Key.F6, InputConstants.KEY_F6);
		put(Key.F7, InputConstants.KEY_F7); put(Key.F8, InputConstants.KEY_F8); put(Key.F9, InputConstants.KEY_F9);
		put(Key.F10, InputConstants.KEY_F10); put(Key.F11, InputConstants.KEY_F11); put(Key.F12, InputConstants.KEY_F12);

		put(Key.NUMPAD_0, InputConstants.KEY_NUMPAD0); put(Key.NUMPAD_1, InputConstants.KEY_NUMPAD1);
		put(Key.NUMPAD_2, InputConstants.KEY_NUMPAD2); put(Key.NUMPAD_3, InputConstants.KEY_NUMPAD3);
		put(Key.NUMPAD_4, InputConstants.KEY_NUMPAD4); put(Key.NUMPAD_5, InputConstants.KEY_NUMPAD5);
		put(Key.NUMPAD_6, InputConstants.KEY_NUMPAD6); put(Key.NUMPAD_7, InputConstants.KEY_NUMPAD7);
		put(Key.NUMPAD_8, InputConstants.KEY_NUMPAD8); put(Key.NUMPAD_9, InputConstants.KEY_NUMPAD9);
		put(Key.NUMPAD_ADD, InputConstants.KEY_ADD); put(Key.NUMPAD_MULTIPLY, InputConstants.KEY_MULTIPLY);
		put(Key.NUMPAD_ENTER, InputConstants.KEY_NUMPADENTER);

		put(Key.UP, InputConstants.KEY_UP); put(Key.DOWN, InputConstants.KEY_DOWN);
		put(Key.LEFT, InputConstants.KEY_LEFT); put(Key.RIGHT, InputConstants.KEY_RIGHT);

		put(Key.SPACE, InputConstants.KEY_SPACE); put(Key.ENTER, InputConstants.KEY_RETURN);
		put(Key.ESCAPE, InputConstants.KEY_ESCAPE); put(Key.TAB, InputConstants.KEY_TAB);
		put(Key.BACKSPACE, InputConstants.KEY_BACKSPACE); put(Key.INSERT, InputConstants.KEY_INSERT);
		put(Key.DELETE, InputConstants.KEY_DELETE); put(Key.HOME, InputConstants.KEY_HOME);
		put(Key.END, InputConstants.KEY_END); put(Key.PAGE_UP, InputConstants.KEY_PAGEUP);
		put(Key.PAGE_DOWN, InputConstants.KEY_PAGEDOWN); put(Key.CAPS_LOCK, InputConstants.KEY_CAPSLOCK);

		put(Key.LEFT_SHIFT, InputConstants.KEY_LSHIFT); put(Key.RIGHT_SHIFT, InputConstants.KEY_RSHIFT);
		put(Key.LEFT_CONTROL, InputConstants.KEY_LCONTROL); put(Key.RIGHT_CONTROL, InputConstants.KEY_RCONTROL);
		put(Key.LEFT_ALT, InputConstants.KEY_LALT); put(Key.RIGHT_ALT, InputConstants.KEY_RALT);

		put(Key.GRAVE, InputConstants.KEY_GRAVE); put(Key.MINUS, InputConstants.KEY_MINUS);
		put(Key.EQUALS, InputConstants.KEY_EQUALS); put(Key.LEFT_BRACKET, InputConstants.KEY_LBRACKET);
		put(Key.RIGHT_BRACKET, InputConstants.KEY_RBRACKET); put(Key.BACKSLASH, InputConstants.KEY_BACKSLASH);
		put(Key.SEMICOLON, InputConstants.KEY_SEMICOLON); put(Key.APOSTROPHE, InputConstants.KEY_APOSTROPHE);
		put(Key.COMMA, InputConstants.KEY_COMMA); put(Key.PERIOD, InputConstants.KEY_PERIOD);
		put(Key.SLASH, InputConstants.KEY_SLASH);
	}

	private KeyTable() {
	}

	private static void put(Key key, int code) {
		BY_CODE.put(code, key);
		BY_KEY.put(key, code);
	}

	static @Nullable Key fromNative(int code) {
		return BY_CODE.get(code);
	}

	/** The native code, or null for keys the game has no constant for. */
	static @Nullable Integer toNative(Key key) {
		return BY_KEY.get(key);
	}
}
