package dev.wisteria.modules.storage;

import java.util.List;

/** The shulker preview's grid as pure arithmetic: nine columns of 18-pixel slots, like the game's own. */
public final class ShulkerLayout {
	public static final int COLUMNS = 9;
	public static final int SLOT = 18;

	private ShulkerLayout() {
	}

	/** Rows for a number of slots (a shulker box has 27: three). */
	public static int rows(int slots) {
		return Math.max(1, (slots + COLUMNS - 1) / COLUMNS);
	}

	/** Top-left of a slot inside the grid. */
	public static int[] cell(int index) {
		return new int[] {(index % COLUMNS) * SLOT, (index / COLUMNS) * SLOT};
	}

	/** "12 of 27 slots · 640 items" from item counts per slot (0 for empty). */
	public static String summary(List<Integer> counts) {
		long used = counts.stream().filter(count -> count > 0).count();
		int items = counts.stream().mapToInt(Integer::intValue).sum();
		return used + " of " + counts.size() + " slots · " + items + " items";
	}
}
