package dev.wisteria.probe;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 0 probe: one working example of every hook the client core will need.
 * Lives in the gametest source set, so it never ends up in the released jar.
 */
public final class Probe implements ClientModInitializer {
	public static final String ID = "wisteria-probe";
	public static final Logger LOG = LoggerFactory.getLogger("WisteriaProbe");

	// Palette from the README, as ARGB.
	static final int SURFACE = 0xE617131F;
	static final int TEXT = 0xFFECE8F5;
	static final int MUTED = 0xFF9A92AD;
	static final int PETAL = 0xFFE9D8FF;
	static final int WISTERIA = 0xFFB79CFF;
	static final int VIOLET = 0xFF8B6CFF;
	static final int DEEP = 0xFF5B3FD6;

	public static final AtomicInteger TICKS_MIXIN = new AtomicInteger();
	public static final AtomicInteger TICKS_FABRIC = new AtomicInteger();
	public static final AtomicInteger HUD_MIXIN = new AtomicInteger();
	public static final AtomicInteger HUD_FABRIC = new AtomicInteger();
	public static final AtomicInteger LEVEL_EXTRACTIONS = new AtomicInteger();
	public static final AtomicInteger KEY_EVENTS = new AtomicInteger();
	public static final AtomicInteger PACKETS_SENT = new AtomicInteger();
	public static final AtomicInteger PACKETS_RECEIVED = new AtomicInteger();
	public static final AtomicInteger PACKETS_CANCELLED = new AtomicInteger();
	public static final Set<String> SENT_TYPES = ConcurrentHashMap.newKeySet();
	public static final Set<String> RECEIVED_TYPES = ConcurrentHashMap.newKeySet();

	/** Block the 3D probe highlights; null = draw nothing. */
	public static volatile BlockPos espTarget;
	/** When set, outgoing packets of this class are dropped (proves cancellation works). */
	public static volatile Class<?> dropOutgoing;
	static volatile String lastKey = "none";

	/**
	 * A custom pipeline: vanilla vertex shader, our own fragment shader. The game compiles the GLSL for
	 * whichever backend is active (OpenGL directly, Vulkan through its GLSL to SPIR-V compiler).
	 */
	public static final RenderPipeline CORNER = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
		.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
		.withLocation(Identifier.fromNamespaceAndPath(ID, "pipeline/corner"))
		.withVertexShader("core/position_tex_color")
		.withFragmentShader(Identifier.fromNamespaceAndPath(ID, "core/corner"))
		.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
		.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
		.withPrimitiveTopology(PrimitiveTopology.QUADS)
		.withCull(false)
		.build();

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> TICKS_FABRIC.incrementAndGet());

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ID, "panel"), (graphics, deltaTracker) -> {
			HUD_FABRIC.incrementAndGet();
			drawPanel(graphics, 8, 8);
		});

		// Per frame, on the main thread, with the gizmo collector active: the place for ESP boxes and tracers.
		LevelExtractionEvents.END_EXTRACTION.register(context -> {
			LEVEL_EXTRACTIONS.incrementAndGet();
			BlockPos target = espTarget;
			if (target == null) {
				return;
			}

			Gizmos.cuboid(new AABB(target), GizmoStyle.strokeAndFill(WISTERIA, 3.0F, 0x558B6CFF)).setAlwaysOnTop();
			Vec3 eye = context.camera().position();
			Vec3 forward = new Vec3(context.camera().forwardVector());
			Gizmos.line(eye.add(forward.scale(0.6)).add(0.0, -0.12, 0.0), Vec3.atCenterOf(target), PETAL, 2.0F).setAlwaysOnTop();
		});

		LOG.info("probe initialised");
	}

	/** Called from {@link dev.wisteria.probe.mixin.HudMixin}: the mixin route to the same 2D API. */
	public static void onHudMixin(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		HUD_MIXIN.incrementAndGet();
		Font font = Minecraft.getInstance().font;
		String label = "drawn from a Hud mixin";
		int x = graphics.guiWidth() - font.width(label) - 8;
		graphics.text(font, label, x, 8, WISTERIA, true);
	}

	public static void onKey(int key, int scancode, int action, int modifiers) {
		KEY_EVENTS.incrementAndGet();
		lastKey = "key=" + key + " action=" + action + " mods=" + modifiers;
	}

	/** @return true to cancel the packet. */
	public static boolean onPacketSend(Packet<?> packet) {
		PACKETS_SENT.incrementAndGet();
		SENT_TYPES.add(packet.getClass().getSimpleName());
		Class<?> drop = dropOutgoing;
		if (drop != null && drop.isInstance(packet)) {
			PACKETS_CANCELLED.incrementAndGet();
			return true;
		}

		return false;
	}

	/** @return true to cancel the packet. */
	public static boolean onPacketReceive(Packet<?> packet) {
		PACKETS_RECEIVED.incrementAndGet();
		RECEIVED_TYPES.add(packet.getClass().getSimpleName());
		return false;
	}

	static void drawPanel(GuiGraphicsExtractor graphics, int x, int y) {
		Font font = Minecraft.getInstance().font;
		int width = 178;
		int height = 64;
		roundedRect(graphics, x, y, width, height, 8, SURFACE);
		graphics.fillGradient(x + 8, y + 9, x + 11, y + height - 9, PETAL, DEEP);
		graphics.text(font, "Wisteria probe", x + 18, y + 9, TEXT, false);
		graphics.text(font, "ticks " + TICKS_MIXIN.get() + "  frames " + HUD_FABRIC.get(), x + 18, y + 21, MUTED, false);
		graphics.text(font, "packets out " + PACKETS_SENT.get() + " in " + PACKETS_RECEIVED.get(), x + 18, y + 32, MUTED, false);
		graphics.text(font, lastKey, x + 18, y + 43, MUTED, false);
		roundedRect(graphics, x + width - 22, y + 8, 14, 14, 7, VIOLET);
	}

	/** Rounded rectangle = three plain fills + four anti-aliased corner quads on the custom pipeline. */
	public static void roundedRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int argb) {
		graphics.fill(x + radius, y, x + width - radius, y + height, argb);
		graphics.fill(x, y + radius, x + radius, y + height - radius, argb);
		graphics.fill(x + width - radius, y + radius, x + width, y + height - radius, argb);
		corner(graphics, x + radius, y + radius, -radius, -radius, argb);
		corner(graphics, x + width - radius, y + radius, radius, -radius, argb);
		corner(graphics, x + radius, y + height - radius, -radius, radius, argb);
		corner(graphics, x + width - radius, y + height - radius, radius, radius, argb);
	}

	private static void corner(GuiGraphicsExtractor graphics, int centerX, int centerY, int dx, int dy, int argb) {
		graphics.guiRenderState.addGuiElement(new CornerRenderState(new Matrix3x2f(graphics.pose()), centerX, centerY, dx, dy, argb));
	}
}
