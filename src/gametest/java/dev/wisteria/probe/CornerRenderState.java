package dev.wisteria.probe;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/** One quarter-disc quad: UV runs from (0,0) at the disc centre to 1 at the outer edges. */
record CornerRenderState(Matrix3x2fc pose, int centerX, int centerY, int dx, int dy, int argb) implements GuiElementRenderState {
	@Override
	public void buildVertices(VertexConsumer vertices) {
		int x0 = Math.min(this.centerX, this.centerX + this.dx);
		int x1 = Math.max(this.centerX, this.centerX + this.dx);
		int y0 = Math.min(this.centerY, this.centerY + this.dy);
		int y1 = Math.max(this.centerY, this.centerY + this.dy);
		this.vertex(vertices, x0, y0);
		this.vertex(vertices, x0, y1);
		this.vertex(vertices, x1, y1);
		this.vertex(vertices, x1, y0);
	}

	private void vertex(VertexConsumer vertices, int x, int y) {
		float u = x == this.centerX ? 0.0F : 1.0F;
		float v = y == this.centerY ? 0.0F : 1.0F;
		vertices.addVertexWith2DPose(this.pose, x, y).setUv(u, v).setColor(this.argb);
	}

	@Override
	public RenderPipeline pipeline() {
		return Probe.CORNER;
	}

	@Override
	public TextureSetup textureSetup() {
		return TextureSetup.noTexture();
	}

	@Override
	public @Nullable ScreenRectangle scissorArea() {
		return null;
	}

	@Override
	public @Nullable ScreenRectangle bounds() {
		int x0 = Math.min(this.centerX, this.centerX + this.dx);
		int y0 = Math.min(this.centerY, this.centerY + this.dy);
		return new ScreenRectangle(x0, y0, Math.abs(this.dx), Math.abs(this.dy)).transformMaxBounds(this.pose);
	}
}
