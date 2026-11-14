package dev.wisteria.modules.xray;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** The ores Xray Predicted knows: the blocks each one comes as, and the colour it gets by default. */
public enum OreKind {
	COAL("Coal", 0xFF3A3A3A, "coal_ore", "deepslate_coal_ore"),
	IRON("Iron", 0xFFD8AF93, "iron_ore", "deepslate_iron_ore"),
	COPPER("Copper", 0xFFE0754F, "copper_ore", "deepslate_copper_ore"),
	GOLD("Gold", 0xFFFCEE4B, "gold_ore", "deepslate_gold_ore"),
	REDSTONE("Redstone", 0xFFFF2B2B, "redstone_ore", "deepslate_redstone_ore"),
	LAPIS("Lapis", 0xFF2E5BDB, "lapis_ore", "deepslate_lapis_ore"),
	DIAMOND("Diamond", 0xFF5DECF5, "diamond_ore", "deepslate_diamond_ore"),
	EMERALD("Emerald", 0xFF17DD62, "emerald_ore", "deepslate_emerald_ore"),
	NETHER_GOLD("Nether Gold", 0xFFF6C443, "nether_gold_ore"),
	QUARTZ("Quartz", 0xFFEAE4DC, "nether_quartz_ore"),
	DEBRIS("Ancient Debris", 0xFF8A5A4A, "ancient_debris");

	private static final Map<String, OreKind> BY_BLOCK = new HashMap<>();

	static {
		for (OreKind kind : values()) {
			for (String block : kind.blocks) {
				BY_BLOCK.put("minecraft:" + block, kind);
			}
		}
	}

	private final String title;
	private final int colour;
	private final String[] blocks;

	OreKind(String title, int colour, String... blocks) {
		this.title = title;
		this.colour = colour;
		this.blocks = blocks;
	}

	public String title() {
		return this.title;
	}

	/** The setting id part: "diamond", "nether_gold". */
	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public int defaultColour() {
		return this.colour;
	}

	/** The ore a block id ("minecraft:deepslate_diamond_ore") is, or null if it is not an ore. */
	public static @Nullable OreKind ofBlock(String blockId) {
		return BY_BLOCK.get(blockId);
	}

	/**
	 * The rock an ore block was before the ore replaced it, so the world can be put back the way generation found it:
	 * deepslate ores were deepslate, nether ores netherrack, the rest stone.
	 */
	public static String hostOf(String oreBlockId) {
		if (oreBlockId.startsWith("minecraft:deepslate_")) {
			return "minecraft:deepslate";
		}

		OreKind kind = ofBlock(oreBlockId);
		return kind == NETHER_GOLD || kind == QUARTZ || kind == DEBRIS ? "minecraft:netherrack" : "minecraft:stone";
	}
}
