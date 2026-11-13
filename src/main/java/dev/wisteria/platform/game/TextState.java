package dev.wisteria.platform.game;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/** The glyphs of one line that live on one atlas page, as a single GUI element of textured quads. */
record TextState(
	Matrix3x2fc pose,
	List<TextRenderer.Quad> quads,
	int argb,
	TextureSetup textureSetup,
	@Nullable ScreenRectangle scissorArea,
	@Nullable ScreenRectangle bounds
) implements GuiElementRenderState {
	@Override
	public void buildVertices(VertexConsumer vertices) {
		for (TextRenderer.Quad quad : this.quads) {
			vertices.addVertexWith2DPose(this.pose, quad.x0(), quad.y0()).setUv(quad.u0(), quad.v0()).setColor(this.argb);
			vertices.addVertexWith2DPose(this.pose, quad.x0(), quad.y1()).setUv(quad.u0(), quad.v1()).setColor(this.argb);
			vertices.addVertexWith2DPose(this.pose, quad.x1(), quad.y1()).setUv(quad.u1(), quad.v1()).setColor(this.argb);
			vertices.addVertexWith2DPose(this.pose, quad.x1(), quad.y0()).setUv(quad.u1(), quad.v0()).setColor(this.argb);
		}
	}

	@Override
	public RenderPipeline pipeline() {
		return RenderPipelines.GUI_TEXTURED;
	}
}
