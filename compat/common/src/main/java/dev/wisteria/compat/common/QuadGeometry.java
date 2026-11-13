package dev.wisteria.compat.common;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/**
 * One GUI quad with float coordinates and a colour per corner. Version-independent: the version-specific
 * render state only adds the pipeline.
 *
 * @param corner if set, UVs are emitted: (0,0) at (centerX, centerY), 1 at the far edges (see the corner shader)
 */
public record QuadGeometry(
	Matrix3x2fc pose,
	float x0, float y0, float x1, float y1,
	int topLeft, int bottomLeft, int bottomRight, int topRight,
	boolean corner, float centerX, float centerY,
	@Nullable ScreenRectangle scissorArea,
	@Nullable ScreenRectangle bounds
) {
	static QuadGeometry plain(Matrix3x2fc pose, float x0, float y0, float x1, float y1,
		int topLeft, int bottomLeft, int bottomRight, int topRight, @Nullable ScreenRectangle scissor) {
		return new QuadGeometry(pose, x0, y0, x1, y1, topLeft, bottomLeft, bottomRight, topRight, false, 0.0F, 0.0F, scissor,
			bounds(pose, x0, y0, x1, y1, scissor));
	}

	static QuadGeometry corner(Matrix3x2fc pose, float centerX, float centerY, float dx, float dy, int argb, @Nullable ScreenRectangle scissor) {
		float x0 = Math.min(centerX, centerX + dx);
		float x1 = Math.max(centerX, centerX + dx);
		float y0 = Math.min(centerY, centerY + dy);
		float y1 = Math.max(centerY, centerY + dy);
		return new QuadGeometry(pose, x0, y0, x1, y1, argb, argb, argb, argb, true, centerX, centerY, scissor, bounds(pose, x0, y0, x1, y1, scissor));
	}

	private static @Nullable ScreenRectangle bounds(Matrix3x2fc pose, float x0, float y0, float x1, float y1, @Nullable ScreenRectangle scissor) {
		int left = (int)Math.floor(x0);
		int top = (int)Math.floor(y0);
		int right = (int)Math.ceil(x1);
		int bottom = (int)Math.ceil(y1);
		ScreenRectangle bounds = new ScreenRectangle(left, top, right - left, bottom - top).transformMaxBounds(pose);
		return scissor != null ? scissor.intersection(bounds) : bounds;
	}

	public void buildVertices(VertexConsumer vertices) {
		this.vertex(vertices, this.x0, this.y0, this.topLeft);
		this.vertex(vertices, this.x0, this.y1, this.bottomLeft);
		this.vertex(vertices, this.x1, this.y1, this.bottomRight);
		this.vertex(vertices, this.x1, this.y0, this.topRight);
	}

	private void vertex(VertexConsumer vertices, float x, float y, int argb) {
		VertexConsumer vertex = vertices.addVertexWith2DPose(this.pose, x, y);
		if (this.corner) {
			vertex = vertex.setUv(x == this.centerX ? 0.0F : 1.0F, y == this.centerY ? 0.0F : 1.0F);
		}

		vertex.setColor(argb);
	}
}
