package dev.wisteria.modules.storage;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Storage ESP's decisions as pure functions over block ids and slot counts. */
public final class StorageLogic {
	public enum Kind {
		CHEST("Chests", 0xFFFFB86B), BARREL("Barrels", 0xFFC9935A), SHULKER("Shulkers", 0xFF9B6FC6), ENDER("Ender Chests", 0xFF5FD3B9),
		OTHER("Other", 0xFF9A92AD);

		private final String title;
		private final int colour;

		Kind(String title, int colour) {
			this.title = title;
			this.colour = colour;
		}

		public String title() {
			return this.title;
		}

		public int defaultColour() {
			return this.colour;
		}
	}

	/** The dye colours shulker boxes come in, as the game's dye map colours. */
	private static final Map<String, Integer> DYES = Map.ofEntries(Map.entry("white", 0xFFF9FFFE), Map.entry("orange", 0xFFF9801D),
		Map.entry("magenta", 0xFFC74EBD), Map.entry("light_blue", 0xFF3AB3DA), Map.entry("yellow", 0xFFFED83D), Map.entry("lime", 0xFF80C71F),
		Map.entry("pink", 0xFFF38BAA), Map.entry("gray", 0xFF474F52), Map.entry("light_gray", 0xFF9D9D97), Map.entry("cyan", 0xFF169C9C),
		Map.entry("purple", 0xFF8932B8), Map.entry("blue", 0xFF3C44AA), Map.entry("brown", 0xFF835432), Map.entry("green", 0xFF5E7C16),
		Map.entry("red", 0xFFB02E26), Map.entry("black", 0xFF1D1D21));

	private StorageLogic() {
	}

	/** What kind of storage a block id is ("minecraft:trapped_chest" → CHEST). */
	public static Kind kindOf(String blockId) {
		String id = blockId.substring(blockId.indexOf(':') + 1);
		if (id.equals("chest") || id.equals("trapped_chest") || id.endsWith("copper_chest")) {
			return Kind.CHEST;
		}

		if (id.equals("barrel")) {
			return Kind.BARREL;
		}

		if (id.endsWith("shulker_box")) {
			return Kind.SHULKER;
		}

		return id.equals("ender_chest") ? Kind.ENDER : Kind.OTHER;
	}

	/** A shulker box's own dye colour, or null for the undyed one and anything else. */
	public static @Nullable Integer shulkerColour(String blockId) {
		String id = blockId.substring(blockId.indexOf(':') + 1);
		if (!id.endsWith("_shulker_box")) {
			return null;
		}

		return DYES.get(id.substring(0, id.length() - "_shulker_box".length()));
	}

	/** Share of slots holding something, 0 to 1. */
	public static float fullness(List<Boolean> slotsUsed) {
		if (slotsUsed.isEmpty()) {
			return 0.0F;
		}

		long used = slotsUsed.stream().filter(Boolean::booleanValue).count();
		return (float) used / slotsUsed.size();
	}

	/**
	 * Fill opacity for a box: outline only (0) while the contents are unknown; once seen, from a faint tint when empty
	 * to {@code maxAlpha} when full.
	 */
	public static int fillAlpha(@Nullable Float fullness, int maxAlpha) {
		if (fullness == null) {
			return 0;
		}

		int faint = Math.min(24, maxAlpha);
		return Math.round(faint + (maxAlpha - faint) * Math.clamp(fullness, 0.0F, 1.0F));
	}

	/** "12/27 slots". */
	public static String fullnessText(int used, int slots) {
		return used + "/" + slots + " slots";
	}
}
