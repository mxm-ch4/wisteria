package dev.wisteria.platform.game;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * How chams are drawn: the entity's model submitted a second time with the client's chams shader, flat or with a
 * glowing silhouette, over terrain or behind it. Pipelines from the game's own builder, so OpenGL and Vulkan alike.
 * The outline mode needs none of this: it is the game's glowing outline in the chams colour.
 */
public final class ChamsRenderTypes {
	/** The chams colour the extraction hook gives a render state; 0 for none. */
	public static final RenderStateDataKey<Integer> COLOR = RenderStateDataKey.create(() -> "wisteria:chams_color");
	/** True for the silhouette glow instead of a flat fill. */
	public static final RenderStateDataKey<Boolean> RIM = RenderStateDataKey.create(() -> "wisteria:chams_rim");
	/** True to draw over terrain. */
	public static final RenderStateDataKey<Boolean> WALLS = RenderStateDataKey.create(() -> "wisteria:chams_walls");

	private static final RenderType[] TYPES = new RenderType[4];

	private ChamsRenderTypes() {
	}

	public static RenderType get(boolean rim, boolean throughWalls) {
		int index = (rim ? 2 : 0) + (throughWalls ? 1 : 0);
		RenderType type = TYPES[index];
		if (type == null) {
			String name = "chams_" + (rim ? "rim" : "flat") + (throughWalls ? "_walls" : "");
			RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
				.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
				.withLocation(Identifier.fromNamespaceAndPath("wisteria", "pipeline/" + name))
				.withVertexShader(Identifier.fromNamespaceAndPath("wisteria", "core/chams"))
				.withFragmentShader(Identifier.fromNamespaceAndPath("wisteria", "core/chams"))
				.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
				.withVertexBinding(0, DefaultVertexFormat.ENTITY)
				.withPrimitiveTopology(PrimitiveTopology.QUADS)
				.withCull(false)
				.withDepthStencilState(new DepthStencilState(throughWalls ? CompareOp.ALWAYS_PASS : CompareOp.GREATER_THAN_OR_EQUAL, false));
			if (rim) {
				builder = builder.withShaderDefine("RIM");
			}

			type = RenderType.create("wisteria_" + name, RenderSetup.builder(builder.build()).sortOnUpload().createRenderSetup());
			TYPES[index] = type;
		}

		return type;
	}
}
