package dev.wisteria.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class HudLayoutTest {
	private static final float W = 960.0F;
	private static final float H = 540.0F;

	@Test
	void placesFromEachAnchor() {
		HudLayout.Rect topLeft = HudLayout.place(new HudLayout.Placement(Anchor.TOP_LEFT, 6.0F, 6.0F), 100.0F, 20.0F, W, H);
		assertEquals(6.0F, topLeft.x());
		assertEquals(6.0F, topLeft.y());
		HudLayout.Rect bottomRight = HudLayout.place(new HudLayout.Placement(Anchor.BOTTOM_RIGHT, -6.0F, -6.0F), 100.0F, 20.0F, W, H);
		assertEquals(W - 106.0F, bottomRight.x());
		assertEquals(H - 26.0F, bottomRight.y());
		HudLayout.Rect centre = HudLayout.place(new HudLayout.Placement(Anchor.CENTER, 0.0F, 40.0F), 100.0F, 20.0F, W, H);
		assertEquals((W - 100.0F) / 2.0F, centre.x());
		assertEquals((H - 20.0F) / 2.0F + 40.0F, centre.y());
	}

	@Test
	void keepsElementsOnScreen() {
		HudLayout.Rect rect = HudLayout.place(new HudLayout.Placement(Anchor.TOP_LEFT, -50.0F, 9000.0F), 100.0F, 20.0F, W, H);
		assertEquals(0.0F, rect.x());
		assertEquals(H - 20.0F, rect.y());
	}

	@Test
	void theAnchorFollowsTheThirdOfTheScreenAndStaysPutOnResize() {
		HudLayout.Placement right = HudLayout.placementAt(850.0F, 10.0F, 100.0F, 20.0F, W, H);
		assertEquals(Anchor.TOP_RIGHT, right.anchor());
		assertEquals(-10.0F, right.dx());
		assertEquals(Anchor.BOTTOM, HudLayout.placementAt(430.0F, 500.0F, 100.0F, 20.0F, W, H).anchor());
		assertEquals(Anchor.LEFT, HudLayout.placementAt(4.0F, 260.0F, 100.0F, 20.0F, W, H).anchor());

		// Same place on this screen; on a wider one it stays 10 px from the right edge.
		HudLayout.Rect same = HudLayout.place(right, 100.0F, 20.0F, W, H);
		assertEquals(850.0F, same.x());
		assertEquals(10.0F, same.y());
		assertEquals(1280.0F - 110.0F, HudLayout.place(right, 100.0F, 20.0F, 1280.0F, 720.0F).x());
	}

	@Test
	void snapsToScreenEdgesAndMiddle() {
		HudLayout.Snapped edge = HudLayout.snap(2.5F, 101.0F, 100.0F, 20.0F, List.of(), W, H, false);
		assertEquals(0.0F, edge.x());
		assertEquals(0.0F, edge.guideX());
		HudLayout.Snapped middle = HudLayout.snap(W / 2.0F - 50.0F + 3.0F, 300.0F, 100.0F, 20.0F, List.of(), W, H, false);
		assertEquals(W / 2.0F - 50.0F, middle.x(), "its centre lines up with the screen's");
		assertEquals(W / 2.0F, middle.guideX());
	}

	@Test
	void snapsToOtherElements() {
		List<HudLayout.Rect> others = List.of(new HudLayout.Rect(200.0F, 100.0F, 80.0F, 30.0F));
		HudLayout.Snapped below = HudLayout.snap(202.5F, 133.0F, 60.0F, 20.0F, others, W, H, true);
		assertEquals(200.0F, below.x(), "left edges line up");
		assertEquals(130.0F, below.y(), "its top meets the other's bottom");
		assertEquals(200.0F, below.guideX());
		assertEquals(130.0F, below.guideY());
	}

	@Test
	void fallsBackToTheGridAwayFromEdges() {
		HudLayout.Snapped free = HudLayout.snap(123.0F, 77.0F, 60.0F, 20.0F, List.of(), W, H, true);
		assertEquals(120.0F, free.x());
		assertEquals(80.0F, free.y());
		assertTrue(Float.isNaN(free.guideX()) && Float.isNaN(free.guideY()), "no guide lines for the grid");
		HudLayout.Snapped exact = HudLayout.snap(123.0F, 77.0F, 60.0F, 20.0F, List.of(), W, H, false);
		assertEquals(123.0F, exact.x(), "without the grid it stays where it was dropped");
	}

	@Test
	void theScreenEdgeWinsOverANearbyElement() {
		// Another element's edge is 1.5 px from where this one's right edge would be; the screen edge is 2 px away.
		List<HudLayout.Rect> others = List.of(new HudLayout.Rect(800.0F, 300.0F, 158.5F, 20.0F));
		HudLayout.Snapped snapped = HudLayout.snap(W - 102.0F, 100.0F, 100.0F, 20.0F, others, W, H, true);
		assertEquals(W - 100.0F, snapped.x());
		assertEquals(W, snapped.guideX());
	}
}
