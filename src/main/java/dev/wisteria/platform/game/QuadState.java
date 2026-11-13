package dev.wisteria.platform.game;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.wisteria.platform.game.QuadGeometry;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.jspecify.annotations.Nullable;

/** Binds version-independent quad geometry to this version's pipeline type. */
record QuadState(RenderPipeline pipeline, QuadGeometry geometry) implements GuiElementRenderState {
	@Override
	public void buildVertices(VertexConsumer vertices) {
		this.geometry.buildVertices(vertices);
	}

	@Override
	public TextureSetup textureSetup() {
		return TextureSetup.noTexture();
	}

	@Override
	public @Nullable ScreenRectangle scissorArea() {
		return this.geometry.scissorArea();
	}

	@Override
	public @Nullable ScreenRectangle bounds() {
		return this.geometry.bounds();
	}
}
