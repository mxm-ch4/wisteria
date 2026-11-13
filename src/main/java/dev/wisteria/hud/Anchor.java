package dev.wisteria.hud;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** The point of the screen a HUD element is pinned to; its offset is measured from there. */
public enum Anchor {
	TOP_LEFT(0, 0), TOP(1, 0), TOP_RIGHT(2, 0),
	LEFT(0, 1), CENTER(1, 1), RIGHT(2, 1),
	BOTTOM_LEFT(0, 2), BOTTOM(1, 2), BOTTOM_RIGHT(2, 2);

	private final int column;
	private final int row;

	Anchor(int column, int row) {
		this.column = column;
		this.row = row;
	}

	/** 0 for the left edge, 0.5 for the middle, 1 for the right edge. */
	public float fx() {
		return this.column / 2.0F;
	}

	public float fy() {
		return this.row / 2.0F;
	}

	public boolean right() {
		return this.column == 2;
	}

	public boolean bottom() {
		return this.row == 2;
	}

	public static Anchor of(int column, int row) {
		return values()[row * 3 + column];
	}

	/** "top-left", as typed in commands and stored in the config. */
	public String id() {
		return this.name().toLowerCase(Locale.ROOT).replace('_', '-');
	}

	public static @Nullable Anchor byId(String id) {
		for (Anchor anchor : values()) {
			if (anchor.id().equalsIgnoreCase(id.replace('_', '-'))) {
				return anchor;
			}
		}

		return null;
	}
}
