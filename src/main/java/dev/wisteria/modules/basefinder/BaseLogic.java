package dev.wisteria.modules.basefinder;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What BaseFinder counts as a sign of players and how a chunk is rated, as pure functions over block ids, tested
 * without the game. Natural structures have some of these blocks too (a village chest, a dungeon spawner, a jungle
 * temple's dispensers), so a chunk needs several signs, or strong ones, to pass the threshold.
 */
public final class BaseLogic {
	/** Kinds of signs, each switchable and with its weight. */
	public enum Feature {
		STORAGE("Storage", 3.0, "chests, barrels, shulker boxes, ender chests"),
		REDSTONE("Redstone", 4.0, "pistons, observers, repeaters, comparators, hoppers, droppers"),
		UTILITY("Utility", 5.0, "beacons, enchanting tables, anvils, brewing stands, respawn anchors, blocks of diamond"),
		BUILDING("Building", 1.0, "concrete, glass, wool, beds, quartz, glazed terracotta, lights and other crafted blocks"),
		PORTAL("Portal", 5.0, "lit nether portals, end portal frames that are filled"),
		SPAWNER("Spawner", 2.0, "mob spawners (natural ones too: counts only with other signs)");

		private final String title;
		private final double weight;
		private final String description;

		Feature(String title, double weight, String description) {
			this.title = title;
			this.weight = weight;
			this.description = description;
		}

		public String title() {
			return this.title;
		}

		public double weight() {
			return this.weight;
		}

		public String description() {
			return this.description;
		}
	}

	private static final Map<String, Feature> EXACT = new HashMap<>();
	private static final String[] COLOURS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
		"purple", "blue", "brown", "green", "red", "black"};

	static {
		for (String id : List.of("chest", "trapped_chest", "barrel", "ender_chest", "shulker_box")) {
			EXACT.put(id, Feature.STORAGE);
		}

		for (String id : List.of("piston", "sticky_piston", "observer", "repeater", "comparator", "hopper", "dropper", "redstone_block", "target",
			"daylight_detector", "crafter", "redstone_lamp", "lever", "note_block")) {
			EXACT.put(id, Feature.REDSTONE);
		}

		for (String id : List.of("beacon", "enchanting_table", "anvil", "chipped_anvil", "damaged_anvil", "brewing_stand", "respawn_anchor",
			"lodestone", "conduit", "netherite_block", "diamond_block", "emerald_block")) {
			EXACT.put(id, Feature.UTILITY);
		}

		for (String id : List.of("glass", "glass_pane", "tinted_glass", "quartz_block", "quartz_pillar", "quartz_bricks", "smooth_quartz",
			"sea_lantern", "slime_block", "honey_block", "iron_block", "gold_block", "copper_block", "glowstone", "shroomlight",
			"lantern", "soul_lantern", "torch", "wall_torch", "soul_torch", "soul_wall_torch", "item_frame", "scaffolding", "dried_kelp_block")) {
			EXACT.put(id, Feature.BUILDING);
		}

		EXACT.put("nether_portal", Feature.PORTAL);
		EXACT.put("spawner", Feature.SPAWNER);
	}

	private BaseLogic() {
	}

	/** The sign a block is ("minecraft:red_concrete" → BUILDING), or null for an ordinary block. */
	public static @Nullable Feature featureOf(String blockId) {
		String id = blockId.startsWith("minecraft:") ? blockId.substring("minecraft:".length()) : blockId;
		Feature exact = EXACT.get(id);
		if (exact != null) {
			return exact;
		}

		for (String colour : COLOURS) {
			if (id.startsWith(colour + "_")) {
				String rest = id.substring(colour.length() + 1);
				switch (rest) {
					case "shulker_box":
						return Feature.STORAGE;
					case "bed", "concrete", "concrete_powder", "stained_glass", "stained_glass_pane", "wool", "glazed_terracotta":
						return Feature.BUILDING;
					default:
						break;
				}
			}
		}

		return null;
	}

	/**
	 * A chunk's rating: for each enabled kind, its weight × (1 + ln(count)), so the first block of a kind counts most and
	 * a wall of glass does not drown everything else. A lone spawner (dungeon) rates 0.
	 */
	public static double score(Map<Feature, Integer> counts, Map<Feature, Boolean> enabled) {
		double score = 0.0;
		int kinds = 0;
		for (Map.Entry<Feature, Integer> entry : counts.entrySet()) {
			int count = entry.getValue();
			if (count <= 0 || !enabled.getOrDefault(entry.getKey(), true)) {
				continue;
			}

			kinds++;
			score += entry.getKey().weight() * (1.0 + Math.log(count));
		}

		if (kinds == 1 && counts.getOrDefault(Feature.SPAWNER, 0) > 0) {
			return 0.0;
		}

		return Math.round(score * 10.0) / 10.0;
	}

	/** One rated chunk; {@code x, y, z} is the middle of its signs. */
	public record Entry(int chunkX, int chunkZ, double score, int blocks, Map<Feature, Integer> counts, int x, int y, int z, long foundAt) {
		public double distanceTo(double px, double pz) {
			return Math.hypot(this.x + 0.5 - px, this.z + 0.5 - pz);
		}

		/** "storage 12, redstone 3" */
		public String summary() {
			StringBuilder text = new StringBuilder();
			this.counts.entrySet().stream().sorted(Map.Entry.<Feature, Integer>comparingByValue().reversed()).forEach(entry -> {
				if (!text.isEmpty()) {
					text.append(", ");
				}

				text.append(entry.getKey().title().toLowerCase(java.util.Locale.ROOT)).append(' ').append(entry.getValue());
			});
			return text.toString();
		}
	}

	public enum Sort {
		SCORE, DISTANCE
	}

	public static List<Entry> sorted(List<Entry> entries, Sort sort, double px, double pz) {
		Comparator<Entry> order = sort == Sort.SCORE
			? Comparator.comparingDouble(Entry::score).reversed().thenComparingDouble(entry -> entry.distanceTo(px, pz))
			: Comparator.<Entry>comparingDouble(entry -> entry.distanceTo(px, pz)).thenComparing(Comparator.comparingDouble(Entry::score).reversed());
		return entries.stream().sorted(order).toList();
	}

	/** Keeps the best {@code limit} by score, so a long trip does not grow the list without end. */
	public static List<Entry> capped(List<Entry> entries, int limit) {
		return entries.size() <= limit ? entries : sorted(entries, Sort.SCORE, 0.0, 0.0).subList(0, limit);
	}

	/** Counts per kind from a list of block ids. */
	public static Map<Feature, Integer> count(Iterable<String> blockIds) {
		Map<Feature, Integer> counts = new EnumMap<>(Feature.class);
		for (String id : blockIds) {
			Feature feature = featureOf(id);
			if (feature != null) {
				counts.merge(feature, 1, Integer::sum);
			}
		}

		return counts;
	}
}
