package dev.wisteria.render;

import java.util.Arrays;

/**
 * Thousands of boxes drawn as one: the geometry goes to the GPU once and is then drawn every frame for next to
 * nothing. For Xray, Search, storage ESP: many boxes that change now and then, not every frame. Keep the batch in
 * a field, rebuild it ({@link #clear} and {@link #add}) only when the set changes, and hand it to
 * {@link Renderer3D#boxes} on every {@code Render3D}. Client thread only.
 *
 * <p>Boxes that move every frame (entities) stay with {@link Renderer3D#box}.
 */
public final class BoxBatch {
	private static final int FLOATS = 6;

	private final float strokeWidth;
	private boolean throughWalls;
	private float[] bounds = new float[FLOATS * 64];
	private int[] colors = new int[2 * 64];
	private int size;
	private double originX;
	private double originY;
	private double originZ;
	private int version;

	/**
	 * @param strokeWidth outline width in pixels, the same for every box of the batch
	 * @param throughWalls true to draw over everything, false to be hidden by blocks in front, as in
	 *     {@link Renderer3D#box}; can be changed later with {@link #setThroughWalls}
	 */
	public BoxBatch(float strokeWidth, boolean throughWalls) {
		this.strokeWidth = strokeWidth;
		this.throughWalls = throughWalls;
	}

	public void clear() {
		if (this.size != 0) {
			this.size = 0;
			this.version++;
		}
	}

	/** Adds a box; an alpha of zero skips that part of it. */
	public void add(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int strokeArgb, int fillArgb) {
		if (this.size == 0) {
			// Coordinates are kept relative to the first box: floats stay precise far from 0,0.
			this.originX = Math.floor(minX);
			this.originY = Math.floor(minY);
			this.originZ = Math.floor(minZ);
		}

		if (this.size * FLOATS == this.bounds.length) {
			this.bounds = Arrays.copyOf(this.bounds, this.bounds.length * 2);
			this.colors = Arrays.copyOf(this.colors, this.colors.length * 2);
		}

		int at = this.size * FLOATS;
		this.bounds[at] = (float)(minX - this.originX);
		this.bounds[at + 1] = (float)(minY - this.originY);
		this.bounds[at + 2] = (float)(minZ - this.originZ);
		this.bounds[at + 3] = (float)(maxX - this.originX);
		this.bounds[at + 4] = (float)(maxY - this.originY);
		this.bounds[at + 5] = (float)(maxZ - this.originZ);
		this.colors[this.size * 2] = strokeArgb;
		this.colors[this.size * 2 + 1] = fillArgb;
		this.size++;
		this.version++;
	}

	public int size() {
		return this.size;
	}

	public float strokeWidth() {
		return this.strokeWidth;
	}

	public boolean throughWalls() {
		return this.throughWalls;
	}

	/** Switches between drawing over blocks and behind them. Takes effect next frame; nothing is re-uploaded. */
	public void setThroughWalls(boolean throughWalls) {
		this.throughWalls = throughWalls;
	}

	/** Changes whenever the contents change; the renderer re-uploads when it does. */
	public int version() {
		return this.version;
	}

	public double originX() {
		return this.originX;
	}

	public double originY() {
		return this.originY;
	}

	public double originZ() {
		return this.originZ;
	}

	/** Bound {@code 0..5} (minX, minY, minZ, maxX, maxY, maxZ) of box {@code index}, relative to the origin. */
	public float bound(int index, int which) {
		return this.bounds[index * FLOATS + which];
	}

	public int stroke(int index) {
		return this.colors[index * 2];
	}

	public int fill(int index) {
		return this.colors[index * 2 + 1];
	}
}
