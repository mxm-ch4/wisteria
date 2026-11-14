package dev.wisteria.modules.xray;

import java.util.Set;

/**
 * The decisions of Xray Predicted as pure functions over plain data, tested without the game. The table is
 * SPEC_AMENDMENT_5.1 §6: what the client sees at a spot the seed says holds an ore decides what is drawn there.
 */
public final class XrayLogic {
	/** Rock ores grow in: what the client sees where an ore was hidden by the server (anti-xray) or simply not mined. */
	static final Set<String> HOST_ROCK = Set.of(
		"minecraft:stone", "minecraft:granite", "minecraft:diorite", "minecraft:andesite", "minecraft:deepslate",
		"minecraft:tuff", "minecraft:netherrack", "minecraft:basalt", "minecraft:blackstone");

	/** What the client sees at the spot. */
	public enum Seen {
		/** Air, water or lava: mined out or never there. */
		OPEN,
		/** Plain rock an ore could be hiding in. */
		HOST,
		/** Exactly the predicted ore. */
		SAME_ORE,
		/** A different ore: the server's fake ores (anti-xray engine mode 2). */
		OTHER_ORE,
		/** Anything else: a player's build, a structure, a later feature. */
		OTHER,
		/** The chunk is not loaded: nothing to check against. */
		UNLOADED
	}

	public enum Verdict {
		/** Not drawn. */
		NONE,
		/** The client sees the ore itself. */
		CONFIRMED,
		/** The seed says an ore is here, the client shows rock (or a fake). */
		PREDICTED,
		/** The seed says an ore is here; the chunk is not loaded, so it could not be checked. */
		UNVERIFIED
	}

	private XrayLogic() {
	}

	/** What a block id at a predicted spot counts as, for the predicted ore. */
	public static Seen see(String blockId, OreKind predicted) {
		if (blockId.equals("minecraft:air") || blockId.equals("minecraft:cave_air") || blockId.equals("minecraft:void_air")
			|| blockId.equals("minecraft:water") || blockId.equals("minecraft:lava")) {
			return Seen.OPEN;
		}

		OreKind ore = OreKind.ofBlock(blockId);
		if (ore != null) {
			return ore == predicted ? Seen.SAME_ORE : Seen.OTHER_ORE;
		}

		return HOST_ROCK.contains(blockId) ? Seen.HOST : Seen.OTHER;
	}

	/** The A5.1 §6 table. */
	public static Verdict classify(Seen seen) {
		return switch (seen) {
			case OPEN, OTHER -> Verdict.NONE;
			case HOST, OTHER_ORE -> Verdict.PREDICTED;
			case SAME_ORE -> Verdict.CONFIRMED;
			case UNLOADED -> Verdict.UNVERIFIED;
		};
	}

	/** Within the horizontal range, in blocks, of the player (a cylinder: depth is what ores are about). */
	public static boolean inRange(int x, int z, double playerX, double playerZ, double range) {
		double dx = x + 0.5 - playerX;
		double dz = z + 0.5 - playerZ;
		return dx * dx + dz * dz <= range * range;
	}

	/**
	 * Parses a seed the way the game's world screen does: a whole number is taken as is, anything else by its
	 * text hash; empty means none.
	 *
	 * @throws IllegalArgumentException when blank
	 */
	public static long parseSeed(String text) {
		String trimmed = text.trim();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("Type the seed");
		}

		try {
			return Long.parseLong(trimmed);
		} catch (NumberFormatException notANumber) {
			return trimmed.hashCode();
		}
	}
}
