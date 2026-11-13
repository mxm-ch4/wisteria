package dev.wisteria.compat.v26_3;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.wisteria.compat.common.CommonPlatform;
import dev.wisteria.compat.common.QuadGeometry;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** Minecraft 26.3: pipelines moved to com.mojang.renderpearl, input is SDL (scancodes instead of GLFW key codes). */
public final class V26_3Platform extends CommonPlatform {
	/** The client's rounded-box shader; shape parameters travel in the ENTITY vertex format (see QuadGeometry). */
	private static final RenderPipeline PANEL = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
		.withBindGroupLayout(BindGroupLayouts.PROJECTION)
		.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
		.withLocation(Identifier.fromNamespaceAndPath("wisteria", "pipeline/panel"))
		.withVertexShader(Identifier.fromNamespaceAndPath("wisteria", "core/panel"))
		.withFragmentShader(Identifier.fromNamespaceAndPath("wisteria", "core/panel"))
		.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
		.withVertexBinding(0, DefaultVertexFormat.ENTITY)
		.withPrimitiveTopology(PrimitiveTopology.QUADS)
		.withCull(false)
		.build();

	@Override
	public String minecraftVersion() {
		return "26.3";
	}

	@Override
	public String graphicsBackend() {
		return RenderSystem.getDevice().getDeviceInfo().backendName();
	}

	@Override
	protected void registerLevelExtraction(Consumer<LevelExtractionContext> handler) {
		LevelExtractionEvents.END_EXTRACTION.register(handler::accept);
	}

	@Override
	protected GuiElementRenderState plainQuad(QuadGeometry geometry) {
		return new QuadState(RenderPipelines.GUI, geometry);
	}

	@Override
	protected GuiElementRenderState panelQuad(QuadGeometry geometry) {
		return new QuadState(PANEL, geometry);
	}

	@Override
	protected boolean isNativeKeyDown(int code) {
		return InputConstants.isKeyDown(code);
	}

	@Override
	protected void setScreen(@Nullable Screen screen) {
		Minecraft.getInstance().gui.setScreen(screen);
	}

	@Override
	protected @Nullable Screen currentScreen() {
		return Minecraft.getInstance().gui.screen();
	}

	@Override
	protected ChatComponent chatComponent() {
		return Minecraft.getInstance().gui.hud.getChat();
	}
}
