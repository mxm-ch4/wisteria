package dev.wisteria.render;

/** Drawing in world coordinates. Colours are ARGB ints; an alpha of zero skips that part. */
public interface Renderer3D {
	double cameraX();

	double cameraY();

	double cameraZ();

	/** Unit vector the camera looks along. */
	float cameraForwardX();

	float cameraForwardY();

	float cameraForwardZ();

	/** Axis-aligned box with an outline and/or a translucent fill. */
	void box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
		int strokeArgb, float strokeWidth, int fillArgb, boolean throughWalls);

	/**
	 * Draws a batch of boxes built earlier. Cheap per frame however many boxes it holds; uploaded again only
	 * after it changed. Use it for anything over a few hundred boxes.
	 */
	void boxes(BoxBatch batch);

	void line(double x1, double y1, double z1, double x2, double y2, double z2, int argb, float width, boolean throughWalls);
}
