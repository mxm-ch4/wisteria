package dev.wisteria.modules.waypoints;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class WaypointLogicTest {
	private static Waypoint at(String name, int x, int z, String dimension, String group, long created) {
		return new Waypoint(name, x, 64, z, dimension, group, 0xFFFFFFFF, created);
	}

	@Test
	void typedLinesAreRead() {
		assertEquals(new WaypointLogic.Parsed("Base", "", null, null, null), WaypointLogic.parse("Base"));
		assertEquals(new WaypointLogic.Parsed("Base", "", 100, null, -200), WaypointLogic.parse("Base 100 -200"));
		assertEquals(new WaypointLogic.Parsed("Old Base", "home", 100, 64, -200), WaypointLogic.parse("Old Base 100 64 -200 @Home"));
		assertEquals(new WaypointLogic.Parsed("Farm 2", "", null, null, null), WaypointLogic.parse("Farm 2"), "a single number stays in the name");
		assertThrows(IllegalArgumentException.class, () -> WaypointLogic.parse("  "));
		assertThrows(IllegalArgumentException.class, () -> WaypointLogic.parse("100 64 -200"));
		assertThrows(IllegalArgumentException.class, () -> WaypointLogic.parse("a·b"));
		assertThrows(IllegalArgumentException.class, () -> WaypointLogic.parse("x".repeat(33)));
	}

	@Test
	void overworldAndNetherShowInEachOtherAtEightToOne() {
		Waypoint home = at("Home", 800, -1601, Waypoint.OVERWORLD, "", 0L);
		assertArrayEquals(new double[] {800, 64, -1601}, WaypointLogic.positionIn(home, Waypoint.OVERWORLD, true));
		assertArrayEquals(new double[] {100, 64, -201}, WaypointLogic.positionIn(home, Waypoint.NETHER, true));
		assertNull(WaypointLogic.positionIn(home, Waypoint.NETHER, false), "off: not shown");
		assertNull(WaypointLogic.positionIn(home, "minecraft:the_end", true), "the End has no 8:1");
		Waypoint portal = at("Portal", 10, -5, Waypoint.NETHER, "", 0L);
		assertArrayEquals(new double[] {80, 64, -40}, WaypointLogic.positionIn(portal, Waypoint.OVERWORLD, true));
	}

	@Test
	void nearestFirstAndOnlyWhatShows() {
		List<Waypoint> all = List.of(at("Far", 1000, 0, Waypoint.OVERWORLD, "", 0L), at("Near", 10, 0, Waypoint.OVERWORLD, "", 0L),
			at("End", 5, 0, "minecraft:the_end", "", 0L));
		assertEquals(List.of("Near", "Far"), WaypointLogic.nearest(all, Waypoint.OVERWORLD, true, 0.0, 0.0).stream().map(Waypoint::name).toList());
	}

	@Test
	void distancesAndLines() {
		assertEquals("340 m", WaypointLogic.distanceText(340.4));
		assertEquals("1.2 km", WaypointLogic.distanceText(1234.0));
		Waypoint base = new Waypoint("Base", 100, 64, -200, Waypoint.OVERWORLD, "home", 0, 0L);
		assertEquals("Base · 100 64 -200 · overworld · @home", WaypointLogic.line(base));
		assertEquals("Base", WaypointLogic.nameOf(WaypointLogic.line(base)));
	}

	@Test
	void onlyTheNewestDeathsAreKept() {
		List<Waypoint> all = List.of(at("Death 1", 0, 0, Waypoint.OVERWORLD, Waypoint.DEATHS, 1L), at("Death 2", 0, 0, Waypoint.OVERWORLD, Waypoint.DEATHS, 2L),
			at("Death 3", 0, 0, Waypoint.OVERWORLD, Waypoint.DEATHS, 3L), at("Home", 0, 0, Waypoint.OVERWORLD, "", 0L));
		assertEquals(List.of("Death 2", "Death 3", "Home"), WaypointLogic.trimDeaths(all, 2).stream().map(Waypoint::name).toList());
	}
}
