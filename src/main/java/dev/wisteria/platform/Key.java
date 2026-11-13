package dev.wisteria.platform;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Keys by name, independent of the windowing library: 26.2 reports GLFW key codes, 26.3 SDL scancodes.
 * Configs store {@link #name()}, never a number.
 */
public enum Key {
	A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z,
	NUM_0, NUM_1, NUM_2, NUM_3, NUM_4, NUM_5, NUM_6, NUM_7, NUM_8, NUM_9,
	F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,
	NUMPAD_0, NUMPAD_1, NUMPAD_2, NUMPAD_3, NUMPAD_4, NUMPAD_5, NUMPAD_6, NUMPAD_7, NUMPAD_8, NUMPAD_9,
	NUMPAD_ADD, NUMPAD_MULTIPLY, NUMPAD_ENTER,
	UP, DOWN, LEFT, RIGHT,
	SPACE, ENTER, ESCAPE, TAB, BACKSPACE, INSERT, DELETE, HOME, END, PAGE_UP, PAGE_DOWN,
	LEFT_SHIFT, RIGHT_SHIFT, LEFT_CONTROL, RIGHT_CONTROL, LEFT_ALT, RIGHT_ALT,
	GRAVE, MINUS, EQUALS, LEFT_BRACKET, RIGHT_BRACKET, BACKSLASH, SEMICOLON, APOSTROPHE, COMMA, PERIOD, SLASH,
	CAPS_LOCK;

	/** Case-insensitive lookup by name; null if there is no such key. */
	public static @Nullable Key byName(String name) {
		try {
			return valueOf(name.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
