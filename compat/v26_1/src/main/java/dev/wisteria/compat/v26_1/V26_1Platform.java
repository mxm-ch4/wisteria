package dev.wisteria.compat.v26_1;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.wisteria.compat.common.CommonPlatform;
import dev.wisteria.compat.common.QuadGeometry;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** Minecraft 26.1.2: OpenGL only, pipelines declare uniforms one by one, no separate Hud or LevelExtractor. */
public final class V26_1Platform extends CommonPlatform {
	/** Vanilla vertex shader, the client's fragment shader: anti-aliased quarter-discs for rounded corners. */
	private static final RenderPipeline CORNER = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
		.withLocation(Identifier.fromNamespaceAndPath("wisteria", "pipeline/corner"))
		.withVertexShader("core/position_tex_color")
		.withFragmentShader(Identifier.fromNamespaceAndPath("wisteria", "core/corner"))
		.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
		.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
		.withCull(false)
		.build();

	@Override
	public String minecraftVersion() {
		return "26.1.2";
	}

	@Override
	public String graphicsBackend() {
		return RenderSystem.getDevice().getBackendName();
	}

	@Override
	protected void registerLevelExtraction(Consumer<LevelExtractionContext> handler) {
		LevelRenderEvents.END_EXTRACTION.register(handler::accept);
	}

	@Override
	protected GuiElementRenderState plainQuad(QuadGeometry geometry) {
		return new QuadState(RenderPipelines.GUI, geometry);
	}

	@Override
	protected GuiElementRenderState cornerQuad(QuadGeometry geometry) {
		return new QuadState(CORNER, geometry);
	}

	@Override
	protected boolean isNativeKeyDown(int code) {
		return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), code);
	}

	@Override
	protected void setScreen(@Nullable Screen screen) {
		Minecraft.getInstance().setScreen(screen);
	}

	@Override
	protected @Nullable Screen currentScreen() {
		return Minecraft.getInstance().screen;
	}

	@Override
	protected ChatComponent chatComponent() {
		return Minecraft.getInstance().gui.getChat();
	}
}
