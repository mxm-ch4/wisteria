package dev.wisteria.render;

/**
 * 2D drawing in GUI-scaled pixels, origin top-left. Colours are ARGB ints. Coordinates are floats so
 * animated elements can sit between pixels.
 */
public interface Renderer2D {
	float width();

	float height();

	void rect(float x, float y, float width, float height, int argb);

	/** Rectangle with a colour per corner: vertical, horizontal and diagonal gradients. */
	void gradient(float x, float y, float width, float height, int topLeft, int topRight, int bottomLeft, int bottomRight);

	/** Filled rectangle with anti-aliased rounded corners. */
	void roundedRect(float x, float y, float width, float height, float radius, int argb);

	/** Rounded rectangle with a colour per corner. */
	void roundedGradient(float x, float y, float width, float height, float radius, int topLeft, int topRight, int bottomLeft, int bottomRight);

	/** Outline of a rounded rectangle, drawn inside its edge. */
	void roundedOutline(float x, float y, float width, float height, float radius, float thickness, int argb);

	/**
	 * A soft halo around a rounded rectangle: a drop shadow in a dark colour, a glow in the accent.
	 * It extends {@code blur} pixels beyond the rectangle and fades out; draw it before the panel it belongs to.
	 */
	void shadow(float x, float y, float width, float height, float radius, float blur, int argb);

	/** Text in the game's own pixel font. */
	void text(String text, float x, float y, int argb, boolean shadow);

	float textWidth(String text);

	float lineHeight();

	/**
	 * Text in one of the client's fonts.
	 *
	 * @param size font size in GUI pixels (the em height)
	 * @param y top of the line box; a line is {@link #lineHeight(FontFace, float)} tall
	 */
	void text(FontFace face, float size, String text, float x, float y, int argb);

	float textWidth(FontFace face, float size, String text);

	float lineHeight(FontFace face, float size);

	/** Clips everything drawn until the matching {@link #popScissor()}; nested regions intersect. */
	void pushScissor(float x, float y, float width, float height);

	void popScissor();

	/** Saves the current transform; restore with {@link #pop()}. */
	void push();

	void pop();

	void translate(float dx, float dy);

	void scale(float sx, float sy);

	/** Rotates everything drawn until the matching {@link #pop()} about the current origin; positive is clockwise. */
	void rotate(float radians);

	/** Blurs everything drawn so far in this frame. The game allows it once per frame; extra calls are ignored. */
	void blurBehind();
}
