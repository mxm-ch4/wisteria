package dev.wisteria.modules.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class StorageLogicTest {
	@Test
	void kinds() {
		assertEquals(StorageLogic.Kind.CHEST, StorageLogic.kindOf("minecraft:chest"));
		assertEquals(StorageLogic.Kind.CHEST, StorageLogic.kindOf("minecraft:trapped_chest"));
		assertEquals(StorageLogic.Kind.BARREL, StorageLogic.kindOf("minecraft:barrel"));
		assertEquals(StorageLogic.Kind.SHULKER, StorageLogic.kindOf("minecraft:shulker_box"));
		assertEquals(StorageLogic.Kind.SHULKER, StorageLogic.kindOf("minecraft:light_blue_shulker_box"));
		assertEquals(StorageLogic.Kind.ENDER, StorageLogic.kindOf("minecraft:ender_chest"));
		assertEquals(StorageLogic.Kind.OTHER, StorageLogic.kindOf("minecraft:hopper"));
	}

	@Test
	void shulkersKeepTheirDye() {
		assertEquals(0xFFB02E26, StorageLogic.shulkerColour("minecraft:red_shulker_box"));
		assertEquals(0xFF3AB3DA, StorageLogic.shulkerColour("minecraft:light_blue_shulker_box"));
		assertNull(StorageLogic.shulkerColour("minecraft:shulker_box"), "undyed: the kind's colour");
		assertNull(StorageLogic.shulkerColour("minecraft:chest"));
	}

	@Test
	void fullnessDrivesTheFill() {
		assertEquals(0.5F, StorageLogic.fullness(List.of(true, false, true, false)), 1e-6);
		assertEquals(0.0F, StorageLogic.fullness(List.of()));
		assertEquals(0, StorageLogic.fillAlpha(null, 120), "unknown: outline only");
		assertEquals(24, StorageLogic.fillAlpha(0.0F, 120), "seen empty: a faint tint");
		assertEquals(120, StorageLogic.fillAlpha(1.0F, 120));
		assertTrue(StorageLogic.fillAlpha(0.5F, 120) > 24 && StorageLogic.fillAlpha(0.5F, 120) < 120);
		assertEquals("12/27 slots", StorageLogic.fullnessText(12, 27));
	}

	@Test
	void shulkerGrid() {
		assertEquals(3, ShulkerLayout.rows(27));
		assertEquals(1, ShulkerLayout.rows(0));
		assertArrayEquals(new int[] {0, 0}, ShulkerLayout.cell(0));
		assertArrayEquals(new int[] {8 * 18, 0}, ShulkerLayout.cell(8));
		assertArrayEquals(new int[] {4 * 18, 18}, ShulkerLayout.cell(13));
		assertEquals("2 of 4 slots · 15 items", ShulkerLayout.summary(List.of(12, 0, 3, 0)));
	}
}
