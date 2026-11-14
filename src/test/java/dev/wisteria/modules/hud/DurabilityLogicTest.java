package dev.wisteria.modules.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DurabilityLogicTest {
	private static DurabilityLogic.Piece piece(String slot, int damage, int max) {
		return new DurabilityLogic.Piece(slot, slot + " item", damage, max);
	}

	@Test
	void bands() {
		assertEquals(DurabilityLogic.Band.GOOD, DurabilityLogic.band(0.5F, 20));
		assertEquals(DurabilityLogic.Band.LOW, DurabilityLogic.band(0.15F, 20));
		assertEquals(DurabilityLogic.Band.CRITICAL, DurabilityLogic.band(0.05F, 20));
		assertEquals(DurabilityLogic.Band.GOOD, DurabilityLogic.band(0.20F, 20), "exactly at the line is still fine");
	}

	@Test
	void shareAndLabels() {
		DurabilityLogic.Piece helmet = piece("head", 300, 363);
		assertEquals(63, helmet.remaining());
		assertEquals("17%", DurabilityLogic.label(helmet, true));
		assertEquals("63", DurabilityLogic.label(helmet, false));
		assertEquals(1.0F, piece("head", 0, 0).left(), "not damageable: full");
		assertEquals(0.0F, piece("head", 500, 363).left(), "never below zero");
	}

	@Test
	void theAlarmSoundsOncePerCrossing() {
		List<DurabilityLogic.Piece> fine = List.of(piece("head", 100, 363));
		List<DurabilityLogic.Piece> low = List.of(piece("head", 300, 363));
		List<DurabilityLogic.Piece> lower = List.of(piece("head", 310, 363));
		List<DurabilityLogic.Piece> critical = List.of(piece("head", 350, 363));
		assertTrue(DurabilityLogic.newlyWorse(fine, fine, 20).isEmpty());
		assertEquals(1, DurabilityLogic.newlyWorse(fine, low, 20).size(), "good to low");
		assertTrue(DurabilityLogic.newlyWorse(low, lower, 20).isEmpty(), "still low: quiet");
		assertEquals(1, DurabilityLogic.newlyWorse(lower, critical, 20).size(), "low to critical");
		assertTrue(DurabilityLogic.newlyWorse(critical, fine, 20).isEmpty(), "repaired: quiet");
		assertEquals(1, DurabilityLogic.newlyWorse(List.of(), low, 20).size(), "put on already low: once");
	}
}
