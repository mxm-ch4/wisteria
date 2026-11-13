package dev.wisteria.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HudScaleTest {
	private static final HudLayout.Rect BOX = new HudLayout.Rect(100.0F, 100.0F, 80.0F, 40.0F);

	@Test
	void staysInRange() {
		assertEquals(0.5F, HudScale.clamp(0.1F));
		assertEquals(3.0F, HudScale.clamp(9.0F));
		assertEquals(3.0F, HudScale.wheel(3.0F, 1.0F, false, false));
		assertEquals(0.5F, HudScale.wheel(0.5F, -1.0F, false, true));
	}

	@Test
	void wheelSteps() {
		assertEquals(1.05F, HudScale.wheel(1.0F, 1.0F, false, false));
		assertEquals(0.99F, HudScale.wheel(1.0F, -1.0F, true, false), "Shift: fine");
		assertEquals(1.25F, HudScale.wheel(1.0F, 3.0F, false, true), "Control: coarse; one notch whatever the amount");
	}

	@Test
	void softSnapToRoundSizes() {
		assertEquals(1.0F, HudScale.snap(1.02F, false));
		assertEquals(1.5F, HudScale.snap(1.48F, false));
		assertEquals(1.38F, HudScale.snap(1.38F, false), "between quarters it stays");
		assertEquals(1.02F, HudScale.snap(1.02F, true), "Shift keeps the exact size");
		assertEquals("1.25x", HudScale.label(1.25F));
		assertEquals("1.5x", HudScale.label(1.5F));
	}

	@Test
	void handlesSitOnTheCorners() {
		assertEquals(0, HudScale.handleAt(BOX, 101.0F, 99.0F));
		assertEquals(3, HudScale.handleAt(BOX, 180.0F, 140.0F));
		assertEquals(1, HudScale.handleAt(BOX, 182.0F, 102.0F));
		assertEquals(-1, HudScale.handleAt(BOX, 140.0F, 120.0F), "the middle is for moving");
	}

	@Test
	void growsFromTheAnchor() {
		// Pinned top left: the bottom-right corner pulled half again as far along the diagonal.
		assertEquals(1.5F, HudScale.dragRatio(BOX, Anchor.TOP_LEFT, 3, 220.0F, 160.0F), 1e-6);
		assertEquals(1.4F, HudScale.dragRatio(BOX, Anchor.TOP_LEFT, 3, 220.0F, 140.0F), 1e-6, "off the diagonal: projected onto it");
		// Pinned on the right: the left corners are the moving side.
		assertEquals(1.5F, HudScale.dragRatio(BOX, Anchor.TOP_RIGHT, 0, 60.0F, 100.0F), 1e-6);
		// Centred: measured from the middle.
		assertEquals(1.5F, HudScale.dragRatio(BOX, Anchor.CENTER, 3, 200.0F, 150.0F), 1e-6);
		// The handle on the anchor point itself is measured from the opposite corner.
		assertEquals(1.5F, HudScale.dragRatio(BOX, Anchor.TOP_LEFT, 0, 60.0F, 80.0F), 1e-6);
	}

	@Test
	void theAnchorPointStaysPutWhenTheSizeChanges() {
		for (Anchor anchor : Anchor.values()) {
			HudLayout.Placement placement = new HudLayout.Placement(anchor, anchor.fx() == 1.0F ? -6.0F : 6.0F, anchor.fy() == 1.0F ? -6.0F : 6.0F);
			HudLayout.Rect small = HudLayout.place(placement, 80.0F, 40.0F, 960.0F, 540.0F);
			HudLayout.Rect big = HudLayout.place(placement, 160.0F, 80.0F, 960.0F, 540.0F);
			assertEquals(small.x() + anchor.fx() * small.width(), big.x() + anchor.fx() * big.width(), 1e-4, anchor + " x");
			assertEquals(small.y() + anchor.fy() * small.height(), big.y() + anchor.fy() * big.height(), 1e-4, anchor + " y");
		}

		HudLayout.Rect huge = HudLayout.place(new HudLayout.Placement(Anchor.BOTTOM_RIGHT, -6.0F, -6.0F), 2000.0F, 900.0F, 960.0F, 540.0F);
		assertEquals(0.0F, huge.x(), "bigger than the screen: still starts on it");
	}
}
