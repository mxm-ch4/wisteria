package dev.wisteria.hud;

/**
 * Sizing HUD elements in the editor, as pure functions: the allowed range, mouse-wheel steps, soft snapping to
 * round values, which corner handle is under the cursor, and how far a dragged handle has scaled the element.
 */
public final class HudScale {
	public static final float MIN = 0.5F;
	public static final float MAX = 3.0F;
	public static final float GLOBAL_MIN = 0.5F;
	public static final float GLOBAL_MAX = 2.0F;
	/** Side of a corner handle, in GUI pixels. */
	public static final float HANDLE = 6.0F;
	/** Within this of 1.0 or a quarter step, a dragged size settles on it. */
	public static final float SNAP = 0.03F;

	private HudScale() {
	}

	public static float clamp(float scale) {
		return Math.max(MIN, Math.min(MAX, scale));
	}

	/**
	 * One notch of the wheel: 0.05, or 0.01 with Shift, or 0.25 with Control.
	 *
	 * @param amount positive when scrolling up (bigger)
	 */
	public static float wheel(float scale, float amount, boolean shift, boolean control) {
		float step = control ? 0.25F : shift ? 0.01F : 0.05F;
		return clamp(round(scale + Math.signum(amount) * step));
	}

	/** Settles on 1.0 or a multiple of 0.25 when close; free keeps the exact value. */
	public static float snap(float scale, boolean free) {
		float clamped = clamp(scale);
		if (free) {
			return round(clamped);
		}

		float quarter = Math.round(clamped * 4.0F) / 4.0F;
		return Math.abs(clamped - quarter) <= SNAP ? quarter : round(clamped);
	}

	private static float round(float scale) {
		return Math.round(scale * 100.0F) / 100.0F;
	}

	/** "1.25x". */
	public static String label(float scale) {
		String text = String.format(java.util.Locale.ROOT, "%.2f", scale);
		return (text.endsWith("0") ? text.substring(0, text.length() - 1) : text) + "x";
	}

	/** Corner handles: 0 top left, 1 top right, 2 bottom left, 3 bottom right. */
	public static float cornerX(HudLayout.Rect rect, int corner) {
		return (corner & 1) == 0 ? rect.x() : rect.right();
	}

	public static float cornerY(HudLayout.Rect rect, int corner) {
		return (corner & 2) == 0 ? rect.y() : rect.bottom();
	}

	/** The corner whose handle is under the point, or -1. */
	public static int handleAt(HudLayout.Rect rect, float x, float y) {
		for (int corner = 0; corner < 4; corner++) {
			if (Math.abs(x - cornerX(rect, corner)) <= HANDLE / 2.0F + 1.0F && Math.abs(y - cornerY(rect, corner)) <= HANDLE / 2.0F + 1.0F) {
				return corner;
			}
		}

		return -1;
	}

	/**
	 * How much bigger the element gets when a handle is dragged from where it was grabbed to where the mouse is.
	 * The element grows from its anchor point, so distances are measured from there; a handle sitting on the anchor
	 * point itself is measured from the opposite corner instead.
	 */
	public static float dragRatio(HudLayout.Rect start, Anchor anchor, int corner, float mouseX, float mouseY) {
		float fixedX = start.x() + anchor.fx() * start.width();
		float fixedY = start.y() + anchor.fy() * start.height();
		float handleX = cornerX(start, corner);
		float handleY = cornerY(start, corner);
		if (Math.abs(handleX - fixedX) < 1.0F && Math.abs(handleY - fixedY) < 1.0F) {
			fixedX = cornerX(start, 3 - corner);
			fixedY = cornerY(start, 3 - corner);
		}

		// The mouse projected onto the line from the fixed point through the handle: proportional, whichever way
		// the mouse wanders off the diagonal.
		float dx = handleX - fixedX;
		float dy = handleY - fixedY;
		float length = dx * dx + dy * dy;
		float ratio = length < 1.0F ? Float.NaN : ((mouseX - fixedX) * dx + (mouseY - fixedY) * dy) / length;
		return Float.isNaN(ratio) ? 1.0F : Math.max(0.01F, ratio);
	}
}
