package dev.wisteria.modules.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class HighwayLogicTest {
	private static HighwayLogic.Nearest at(double x, double z, List<Integer> rings) {
		return HighwayLogic.nearest(x, z, true, true, rings);
	}

	@Test
	void axes() {
		HighwayLogic.Nearest east = at(5000, 3, List.of());
		assertEquals("+X axis", east.name());
		assertEquals(3.0, east.offset(), 1e-9);
		assertEquals(-90.0F, east.travelYaw(), 1e-4, "east is yaw -90");
		assertEquals("-Z axis", at(-4, -9000, List.of()).name());
		assertEquals(180.0F, Math.abs(at(-4, -9000, List.of()).travelYaw()), 1e-4, "north is yaw ±180");
	}

	@Test
	void diagonals() {
		HighwayLogic.Nearest diagonal = at(1000, 1002, List.of());
		assertEquals("+X+Z diagonal", diagonal.name());
		assertEquals(Math.sqrt(2.0), diagonal.offset(), 1e-9);
		assertEquals("-X-Z diagonal", at(-700, -705, List.of()).name());
	}

	@Test
	void ringRoads() {
		HighwayLogic.Nearest ring = at(300, 1003, List.of(1000));
		assertEquals("ring 1000", ring.name());
		assertEquals(3.0, ring.offset(), 1e-9);
		assertEquals(90.0F, ring.travelYaw(), 1e-4, "south side, clockwise: west");
		assertEquals("+X axis", at(500, 2, List.of(1000)).name(), "the axis is nearer than the ring");
		assertEquals(List.of(1000, 5000), HighwayLogic.parseRings("1000, 5000 x -3"));
	}

	@Test
	void nothingSwitchedOn() {
		assertNull(HighwayLogic.nearest(10, 10, false, false, List.of()));
	}

	@Test
	void guidanceAndTurning() {
		HighwayLogic.Nearest east = at(5000, 6, List.of());
		assertEquals("on it", HighwayLogic.guidance(at(5000, 1, List.of()), 5000, 1, 2.0));
		assertEquals("6 off · go N", HighwayLogic.guidance(east, 5000, 6, 2.0));
		assertEquals(10.0F, HighwayLogic.turn(-100.0F, -90.0F), 1e-4);
		assertEquals(-20.0F, HighwayLogic.turn(170.0F, 150.0F), 1e-4);
		assertEquals(20.0F, HighwayLogic.turn(170.0F, -170.0F), 1e-4, "across the wrap");
		assertEquals("S", HighwayLogic.compass(0.0F));
		assertEquals("E", HighwayLogic.compass(-90.0F));
		assertEquals("NW", HighwayLogic.compass(135.0F));
	}
}
