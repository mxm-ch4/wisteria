package dev.wisteria.compat;

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

	void text(String text, float x, float y, int argb, boolean shadow);

	float textWidth(String text);

	float lineHeight();

	/** Clips everything drawn until the matching {@link #popScissor()}; nested regions intersect. */
	void pushScissor(float x, float y, float width, float height);

	void popScissor();

	/** Saves the current transform; restore with {@link #pop()}. */
	void push();

	void pop();

	void translate(float dx, float dy);

	void scale(float sx, float sy);

	/** Blurs everything drawn so far in this frame. The game allows it once per frame; extra calls are ignored. */
	void blurBehind();
}
