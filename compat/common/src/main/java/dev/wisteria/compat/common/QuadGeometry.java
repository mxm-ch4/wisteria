package dev.wisteria.compat.common;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/**
 * One GUI quad with float coordinates and a colour per corner. Version-independent: the version-specific
 * render state only adds the pipeline.
 *
 * <p>A plain quad is position and colour. A panel quad carries a rounded-box description for the panel shader,
 * packed into vertex attributes the game already has, so no custom uniforms are needed:
 * UV0 = position relative to the box centre, UV1 = half size, UV2 = (radius, stroke), Normal.x = feather.
 * Lengths in UV1/UV2 are in 1/8 GUI pixel.
 */
public record QuadGeometry(
	Matrix3x2fc pose,
	float x0, float y0, float x1, float y1,
	int topLeft, int bottomLeft, int bottomRight, int topRight,
	@Nullable Panel panel,
	@Nullable ScreenRectangle scissorArea,
	@Nullable ScreenRectangle bounds
) {
	private static final float UNITS = 8.0F;

	/**
	 * @param stroke outline thickness; zero fills the box
	 * @param feather how far the edge fades out, for shadows and glows; zero for a crisp anti-aliased edge
	 */
	public record Panel(float centerX, float centerY, float halfWidth, float halfHeight, float radius, float stroke, float feather) {
	}

	static QuadGeometry plain(Matrix3x2fc pose, float x0, float y0, float x1, float y1,
		int topLeft, int bottomLeft, int bottomRight, int topRight, @Nullable ScreenRectangle scissor) {
		return new QuadGeometry(pose, x0, y0, x1, y1, topLeft, bottomLeft, bottomRight, topRight, null, scissor, bounds(pose, x0, y0, x1, y1, scissor));
	}

	/** A rounded box of the given size; the quad itself is grown by {@code feather} so the fade has room. */
	static QuadGeometry panel(Matrix3x2fc pose, float x, float y, float width, float height, float radius, float stroke, float feather,
		int topLeft, int bottomLeft, int bottomRight, int topRight, @Nullable ScreenRectangle scissor) {
		Panel panel = new Panel(x + width / 2.0F, y + height / 2.0F, width / 2.0F, height / 2.0F, radius, stroke, feather);
		float x0 = x - feather;
		float y0 = y - feather;
		float x1 = x + width + feather;
		float y1 = y + height + feather;
		return new QuadGeometry(pose, x0, y0, x1, y1, topLeft, bottomLeft, bottomRight, topRight, panel, scissor, bounds(pose, x0, y0, x1, y1, scissor));
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
		VertexConsumer vertex = vertices.addVertexWith2DPose(this.pose, x, y).setColor(argb);
		Panel shape = this.panel;
		if (shape != null) {
			vertex.setUv(x - shape.centerX, y - shape.centerY)
				.setUv1(units(shape.halfWidth), units(shape.halfHeight))
				.setUv2(units(shape.radius), units(shape.stroke))
				.setNormal(Math.min(1.0F, shape.feather / 127.0F), 0.0F, 0.0F);
		}
	}

	private static int units(float length) {
		return Math.max(0, Math.min(Short.MAX_VALUE, Math.round(length * UNITS)));
	}
}
