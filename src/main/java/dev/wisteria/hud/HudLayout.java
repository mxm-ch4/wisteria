package dev.wisteria.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * Where HUD elements go, as pure functions of plain numbers: placing an element from its anchor and offset,
 * picking the anchor for a spot it was dragged to, and snapping while dragging. No game, no drawing.
 */
public final class HudLayout {
	/** Distance in GUI pixels within which an edge snaps. */
	public static final float SNAP = 4.0F;
	/** The editor's grid. */
	public static final float GRID = 8.0F;

	private HudLayout() {
	}

	/** An anchor plus the offset from it, in GUI pixels; what is saved. */
	public record Placement(Anchor anchor, float dx, float dy) {
	}

	public record Rect(float x, float y, float width, float height) {
		public float right() {
			return this.x + this.width;
		}

		public float bottom() {
			return this.y + this.height;
		}

		public boolean contains(float px, float py) {
			return px >= this.x && px < this.x + this.width && py >= this.y && py < this.y + this.height;
		}
	}

	/**
	 * Top-left corner of an element: the anchor's point on the screen lines up with the same point on the
	 * element, then the offset is added. Kept fully on screen.
	 */
	public static Rect place(Placement placement, float width, float height, float screenWidth, float screenHeight) {
		Anchor anchor = placement.anchor();
		float x = anchor.fx() * (screenWidth - width) + placement.dx();
		float y = anchor.fy() * (screenHeight - height) + placement.dy();
		x = Math.max(0.0F, Math.min(x, screenWidth - width));
		y = Math.max(0.0F, Math.min(y, screenHeight - height));
		return new Rect(x, y, width, height);
	}

	/**
	 * The placement for an element whose top-left corner was dropped at x, y: anchored to the third of the screen
	 * its centre is in, so it stays in that corner or edge when the window is resized.
	 */
	public static Placement placementAt(float x, float y, float width, float height, float screenWidth, float screenHeight) {
		int column = third(x + width / 2.0F, screenWidth);
		int row = third(y + height / 2.0F, screenHeight);
		Anchor anchor = Anchor.of(column, row);
		return new Placement(anchor, x - anchor.fx() * (screenWidth - width), y - anchor.fy() * (screenHeight - height));
	}

	private static int third(float centre, float size) {
		return centre < size / 3.0F ? 0 : centre > size * 2.0F / 3.0F ? 2 : 1;
	}

	/**
	 * Where a dragged element ends up and which guide lines to show.
	 *
	 * @param others the other elements on screen
	 * @param grid true to round to the grid where no edge is near
	 */
	public static Snapped snap(float x, float y, float width, float height, List<Rect> others, float screenWidth, float screenHeight, boolean grid) {
		List<Float> verticals = new ArrayList<>(List.of(0.0F, screenWidth / 2.0F, screenWidth));
		List<Float> horizontals = new ArrayList<>(List.of(0.0F, screenHeight / 2.0F, screenHeight));
		for (Rect other : others) {
			verticals.add(other.x());
			verticals.add(other.x() + other.width() / 2.0F);
			verticals.add(other.right());
			horizontals.add(other.y());
			horizontals.add(other.y() + other.height() / 2.0F);
			horizontals.add(other.bottom());
		}

		Axis horizontal = snapAxis(x, width, List.of(0.0F, screenWidth), verticals, grid);
		Axis vertical = snapAxis(y, height, List.of(0.0F, screenHeight), horizontals, grid);
		float snappedX = Math.max(0.0F, Math.min(horizontal.position, screenWidth - width));
		float snappedY = Math.max(0.0F, Math.min(vertical.position, screenHeight - height));
		return new Snapped(snappedX, snappedY, horizontal.guide, vertical.guide);
	}

	/**
	 * @param guideX a vertical line to draw at this x, or NaN
	 * @param guideY a horizontal line to draw at this y, or NaN
	 */
	public record Snapped(float x, float y, float guideX, float guideY) {
	}

	private record Axis(float position, float guide) {
	}

	// The screen's own edges come first: within SNAP of one, that is where it goes. Otherwise the element's start,
	// middle and end are each tried against every line; the closest within SNAP wins.
	private static Axis snapAxis(float start, float size, List<Float> edges, List<Float> lines, boolean grid) {
		if (start < SNAP + 0.001F && start > -SNAP) {
			return new Axis(edges.get(0), edges.get(0));
		}

		if (Math.abs(start + size - edges.get(1)) < SNAP + 0.001F) {
			return new Axis(edges.get(1) - size, edges.get(1));
		}

		float best = SNAP + 0.001F;
		float position = start;
		float guide = Float.NaN;
		float[] parts = {0.0F, size / 2.0F, size};
		for (float line : lines) {
			for (float edge : parts) {
				float distance = Math.abs(start + edge - line);
				if (distance < best) {
					best = distance;
					position = line - edge;
					guide = line;
				}
			}
		}

		if (Float.isNaN(guide) && grid) {
			position = Math.round(start / GRID) * GRID;
		}

		return new Axis(position, guide);
	}
}
