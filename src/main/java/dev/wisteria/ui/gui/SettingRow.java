package dev.wisteria.ui.gui;

import dev.wisteria.platform.InputBackend;
import dev.wisteria.platform.Key;
import dev.wisteria.platform.ScreenBackend.MouseButton;

/** One line (or expandable block) in a module's settings: a label and the control for one value. */
abstract class SettingRow {
	static final float HEIGHT = 15.0F;

	protected String id = "";
	private final String label;
	private final String description;
	protected float x;
	protected float y;
	protected float width;

	protected SettingRow(String label, String description) {
		this.label = label;
		this.description = description;
	}

	/** Gives the row its control id; done by the module row that owns it. */
	final void assignId(String id) {
		this.id = id;
	}

	final String label() {
		return this.label;
	}

	final String description() {
		return this.description;
	}

	/** Current height, which changes while a dropdown or a picker opens. */
	abstract float height();

	final void layout(float x, float y, float width) {
		this.x = x;
		this.y = y;
		this.width = width;
	}

	abstract void render(Ui ui);

	final boolean contains(float px, float py) {
		return Ui.inside(px, py, this.x, this.y, this.width, this.height());
	}

	/** @return true if the click was used */
	boolean mouseDown(float px, float py, MouseButton button) {
		return false;
	}

	void mouseUp() {
	}

	/** True while the row is waiting for keyboard input: a focused text field, a bind being recorded. */
	boolean capturesKeys() {
		return false;
	}

	boolean keyDown(Key key, boolean control, InputBackend input) {
		return false;
	}

	void charTyped(String text) {
	}

	/** The user clicked elsewhere: give up focus, stop listening. */
	void blur() {
	}
}
