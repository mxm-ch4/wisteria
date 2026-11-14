package dev.wisteria.modules.basefinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** BaseFinder's rating on synthetic chunks: a base, a village, a dungeon, plain terrain. */
class BaseLogicTest {
	private static final Map<BaseLogic.Feature, Boolean> ALL = new EnumMap<>(BaseLogic.Feature.class);

	private static List<String> chunk(Object... idAndCount) {
		List<String> blocks = new ArrayList<>();
		for (int i = 0; i < idAndCount.length; i += 2) {
			blocks.addAll(Collections.nCopies((Integer) idAndCount[i + 1], "minecraft:" + idAndCount[i]));
		}

		return blocks;
	}

	@Test
	void blocksAreSorted() {
		assertEquals(BaseLogic.Feature.STORAGE, BaseLogic.featureOf("minecraft:barrel"));
		assertEquals(BaseLogic.Feature.STORAGE, BaseLogic.featureOf("minecraft:light_blue_shulker_box"));
		assertEquals(BaseLogic.Feature.REDSTONE, BaseLogic.featureOf("minecraft:observer"));
		assertEquals(BaseLogic.Feature.BUILDING, BaseLogic.featureOf("minecraft:red_bed"));
		assertEquals(BaseLogic.Feature.UTILITY, BaseLogic.featureOf("minecraft:beacon"));
		assertEquals(BaseLogic.Feature.BUILDING, BaseLogic.featureOf("minecraft:black_concrete"));
		assertEquals(BaseLogic.Feature.BUILDING, BaseLogic.featureOf("minecraft:light_gray_stained_glass_pane"));
		assertEquals(BaseLogic.Feature.PORTAL, BaseLogic.featureOf("minecraft:nether_portal"));
		assertEquals(BaseLogic.Feature.SPAWNER, BaseLogic.featureOf("minecraft:spawner"));
		assertNull(BaseLogic.featureOf("minecraft:stone"));
		assertNull(BaseLogic.featureOf("minecraft:oak_planks"));
		assertNull(BaseLogic.featureOf("minecraft:orange_terracotta"), "badlands make coloured terracotta");
	}

	@Test
	void aBaseOutratesAVillageAndADungeon() {
		double base = BaseLogic.score(BaseLogic.count(chunk("chest", 24, "hopper", 6, "observer", 4, "enchanting_table", 1, "white_concrete", 120,
			"glass", 40)), ALL);
		double village = BaseLogic.score(BaseLogic.count(chunk("chest", 1, "white_bed", 2, "glass_pane", 6, "torch", 4)), ALL);
		double dungeon = BaseLogic.score(BaseLogic.count(chunk("spawner", 1)), ALL);
		double terrain = BaseLogic.score(BaseLogic.count(chunk("stone", 5000, "dirt", 300)), ALL);
		assertTrue(base > 30.0, "base " + base);
		assertTrue(village < 10.0, "village " + village + " stays under the default threshold");
		assertEquals(0.0, dungeon, "a lone spawner is a dungeon");
		assertEquals(0.0, terrain);
	}

	@Test
	void oneWallDoesNotDrownTheRest() {
		double glassWall = BaseLogic.score(BaseLogic.count(chunk("glass", 2000)), ALL);
		double smallBase = BaseLogic.score(BaseLogic.count(chunk("chest", 4, "hopper", 1, "red_bed", 1)), ALL);
		assertTrue(glassWall < 10.0, "glass " + glassWall);
		assertTrue(smallBase > glassWall, "small base " + smallBase);
	}

	@Test
	void switchedOffKindsDoNotCount() {
		Map<BaseLogic.Feature, Boolean> noStorage = new EnumMap<>(BaseLogic.Feature.class);
		noStorage.put(BaseLogic.Feature.STORAGE, false);
		assertEquals(0.0, BaseLogic.score(BaseLogic.count(chunk("chest", 50)), noStorage));
	}

	@Test
	void sortingByScoreOrDistanceAndTheCap() {
		BaseLogic.Entry near = new BaseLogic.Entry(0, 0, 12.0, 5, Map.of(), 10, 64, 10, 0L);
		BaseLogic.Entry far = new BaseLogic.Entry(50, 50, 40.0, 30, Map.of(), 800, 64, 800, 0L);
		BaseLogic.Entry mid = new BaseLogic.Entry(10, 10, 20.0, 9, Map.of(), 160, 64, 160, 0L);
		List<BaseLogic.Entry> all = List.of(near, far, mid);
		assertEquals(List.of(far, mid, near), BaseLogic.sorted(all, BaseLogic.Sort.SCORE, 0.0, 0.0));
		assertEquals(List.of(near, mid, far), BaseLogic.sorted(all, BaseLogic.Sort.DISTANCE, 0.0, 0.0));
		assertEquals(List.of(far, mid), BaseLogic.capped(all, 2));
	}

	@Test
	void summaryNamesTheBiggestFirst() {
		BaseLogic.Entry entry = new BaseLogic.Entry(0, 0, 1.0, 15, Map.of(BaseLogic.Feature.STORAGE, 12, BaseLogic.Feature.REDSTONE, 3), 0, 0, 0, 0L);
		assertEquals("storage 12, redstone 3", entry.summary());
	}
}
