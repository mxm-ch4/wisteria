package dev.wisteria.modules.xray;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The A5.1 §6 table on synthetic chunks: a made-up chunk of block ids, a list of spots the seed "predicts", and the
 * verdict each spot gets. The same as a Paper server with anti-xray would show the client, without a server.
 */
class XrayLogicTest {
	/** A synthetic 16×16×16 chunk: stone everywhere unless set; null chunk means not loaded. */
	private static final class SyntheticChunk {
		private final Map<Integer, String> blocks = new HashMap<>();

		SyntheticChunk set(int x, int y, int z, String id) {
			this.blocks.put(x + z * 16 + y * 256, id);
			return this;
		}

		String at(int x, int y, int z) {
			return this.blocks.getOrDefault(x + z * 16 + y * 256, "minecraft:stone");
		}
	}

	private record Spot(int x, int y, int z, OreKind kind) {
	}

	private static Map<XrayLogic.Verdict, Integer> classifyAll(SyntheticChunk chunk, List<Spot> spots) {
		Map<XrayLogic.Verdict, Integer> counts = new EnumMap<>(XrayLogic.Verdict.class);
		for (Spot spot : spots) {
			XrayLogic.Seen seen = chunk == null ? XrayLogic.Seen.UNLOADED : XrayLogic.see(chunk.at(spot.x(), spot.y(), spot.z()), spot.kind());
			counts.merge(XrayLogic.classify(seen), 1, Integer::sum);
		}

		return counts;
	}

	@Test
	void everyRowOfTheTable() {
		assertEquals(XrayLogic.Verdict.NONE, XrayLogic.classify(XrayLogic.see("minecraft:air", OreKind.DIAMOND)));
		assertEquals(XrayLogic.Verdict.NONE, XrayLogic.classify(XrayLogic.see("minecraft:cave_air", OreKind.DIAMOND)));
		assertEquals(XrayLogic.Verdict.NONE, XrayLogic.classify(XrayLogic.see("minecraft:water", OreKind.DIAMOND)));
		assertEquals(XrayLogic.Verdict.NONE, XrayLogic.classify(XrayLogic.see("minecraft:lava", OreKind.DIAMOND)));
		assertEquals(XrayLogic.Verdict.PREDICTED, XrayLogic.classify(XrayLogic.see("minecraft:stone", OreKind.DIAMOND)));
		assertEquals(XrayLogic.Verdict.PREDICTED, XrayLogic.classify(XrayLogic.see("minecraft:deepslate", OreKind.DIAMOND)));
		assertEquals(XrayLogic.Verdict.PREDICTED, XrayLogic.classify(XrayLogic.see("minecraft:netherrack", OreKind.DEBRIS)));
		assertEquals(XrayLogic.Verdict.CONFIRMED, XrayLogic.classify(XrayLogic.see("minecraft:deepslate_diamond_ore", OreKind.DIAMOND)));
		assertEquals(XrayLogic.Verdict.CONFIRMED, XrayLogic.classify(XrayLogic.see("minecraft:diamond_ore", OreKind.DIAMOND)));
		assertEquals(XrayLogic.Verdict.PREDICTED, XrayLogic.classify(XrayLogic.see("minecraft:gold_ore", OreKind.DIAMOND)));
		assertEquals(XrayLogic.Verdict.NONE, XrayLogic.classify(XrayLogic.see("minecraft:oak_planks", OreKind.DIAMOND)));
		assertEquals(XrayLogic.Verdict.NONE, XrayLogic.classify(XrayLogic.see("minecraft:cobblestone", OreKind.DIAMOND)));
		assertEquals(XrayLogic.Verdict.UNVERIFIED, XrayLogic.classify(XrayLogic.Seen.UNLOADED));
	}

	@Test
	void vanillaServerShowsTheOres() {
		// No anti-xray: every predicted ore is there, one was mined out.
		SyntheticChunk chunk = new SyntheticChunk()
			.set(3, 5, 3, "minecraft:diamond_ore").set(4, 5, 3, "minecraft:diamond_ore").set(4, 6, 3, "minecraft:air")
			.set(9, 2, 9, "minecraft:deepslate_gold_ore");
		Map<XrayLogic.Verdict, Integer> counts = classifyAll(chunk, List.of(
			new Spot(3, 5, 3, OreKind.DIAMOND), new Spot(4, 5, 3, OreKind.DIAMOND), new Spot(4, 6, 3, OreKind.DIAMOND),
			new Spot(9, 2, 9, OreKind.GOLD)));
		assertEquals(3, counts.get(XrayLogic.Verdict.CONFIRMED));
		assertEquals(1, counts.get(XrayLogic.Verdict.NONE));
		assertNull(counts.get(XrayLogic.Verdict.PREDICTED));
	}

	@Test
	void antiXrayHidingOresAsStone() {
		// Engine mode 1: hidden ores are sent as stone; the prediction still marks them.
		SyntheticChunk chunk = new SyntheticChunk().set(1, 1, 1, "minecraft:diamond_ore");
		Map<XrayLogic.Verdict, Integer> counts = classifyAll(chunk, List.of(
			new Spot(1, 1, 1, OreKind.DIAMOND), new Spot(7, 7, 7, OreKind.DIAMOND), new Spot(8, 7, 7, OreKind.DIAMOND)));
		assertEquals(1, counts.get(XrayLogic.Verdict.CONFIRMED));
		assertEquals(2, counts.get(XrayLogic.Verdict.PREDICTED));
	}

	@Test
	void antiXrayFillingWithFakeOres() {
		// Engine mode 2: random ores everywhere; a predicted diamond that reads as redstone is still predicted.
		SyntheticChunk chunk = new SyntheticChunk();
		for (int x = 0; x < 16; x++) {
			chunk.set(x, 4, 0, x % 2 == 0 ? "minecraft:redstone_ore" : "minecraft:emerald_ore");
		}

		Map<XrayLogic.Verdict, Integer> counts = classifyAll(chunk, List.of(new Spot(2, 4, 0, OreKind.DIAMOND), new Spot(3, 4, 0, OreKind.EMERALD)));
		assertEquals(1, counts.get(XrayLogic.Verdict.PREDICTED));
		assertEquals(1, counts.get(XrayLogic.Verdict.CONFIRMED));
	}

	@Test
	void buildsAndUnloadedChunks() {
		SyntheticChunk base = new SyntheticChunk().set(5, 5, 5, "minecraft:chest").set(6, 5, 5, "minecraft:stone_bricks");
		Map<XrayLogic.Verdict, Integer> counts = classifyAll(base, List.of(new Spot(5, 5, 5, OreKind.IRON), new Spot(6, 5, 5, OreKind.IRON)));
		assertEquals(2, counts.get(XrayLogic.Verdict.NONE));
		assertEquals(3, classifyAll(null, List.of(new Spot(0, 0, 0, OreKind.COAL), new Spot(1, 0, 0, OreKind.COAL), new Spot(2, 0, 0, OreKind.COAL)))
			.get(XrayLogic.Verdict.UNVERIFIED));
	}

	@Test
	void oresAndTheirRock() {
		assertEquals(OreKind.DIAMOND, OreKind.ofBlock("minecraft:deepslate_diamond_ore"));
		assertEquals(OreKind.DEBRIS, OreKind.ofBlock("minecraft:ancient_debris"));
		assertNull(OreKind.ofBlock("minecraft:stone"));
		assertEquals("minecraft:deepslate", OreKind.hostOf("minecraft:deepslate_iron_ore"));
		assertEquals("minecraft:stone", OreKind.hostOf("minecraft:iron_ore"));
		assertEquals("minecraft:netherrack", OreKind.hostOf("minecraft:nether_quartz_ore"));
	}

	@Test
	void rangeIsACylinder() {
		assertTrue(XrayLogic.inRange(10, 0, 0.5, 0.5, 10.0));
		assertFalse(XrayLogic.inRange(11, 0, 0.5, 0.5, 10.0));
		assertTrue(XrayLogic.inRange(0, 0, 0.5, 0.5, 1.0));
	}

	@Test
	void seedsParseLikeTheWorldScreen() {
		assertEquals(20261114L, XrayLogic.parseSeed(" 20261114 "));
		assertEquals(-42L, XrayLogic.parseSeed("-42"));
		assertEquals("wisteria".hashCode(), XrayLogic.parseSeed("wisteria"));
		assertThrows(IllegalArgumentException.class, () -> XrayLogic.parseSeed("  "));
	}
}
