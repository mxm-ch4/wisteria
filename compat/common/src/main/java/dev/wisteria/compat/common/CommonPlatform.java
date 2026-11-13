package dev.wisteria.compat.common;

import dev.wisteria.compat.AudioBackend;
import dev.wisteria.compat.ClientListener;
import dev.wisteria.compat.InputBackend;
import dev.wisteria.compat.PacketAdapter;
import dev.wisteria.compat.Platform;
import dev.wisteria.compat.ScreenBackend;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The part of the platform that compiles unchanged against every supported Minecraft version.
 * A version subclass fills in what differs: render pipelines, the level extraction event, key state, screens.
 */
public abstract class CommonPlatform implements Platform {
	private static @Nullable CommonPlatform instance;

	private final List<ClientListener> listeners = new CopyOnWriteArrayList<>();
	private final CommonInput input = new CommonInput(this);
	private final CommonAudio audio = new CommonAudio();
	private final CommonPackets packets = new CommonPackets();
	private final CommonScreens screens = new CommonScreens(this);

	/** The installed platform, or null before the mod initialises. Mixins go through this. */
	public static @Nullable CommonPlatform instance() {
		return instance;
	}

	/** Hooks the game events. Called once by the version entrypoint. */
	public final void install() {
		instance = this;

		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			for (ClientListener listener : this.listeners) {
				listener.onTick();
			}
		});

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("wisteria", "hud"), (graphics, deltaTracker) -> {
			if (this.listeners.isEmpty()) {
				return;
			}

			GuiRenderer2D renderer = new GuiRenderer2D(this, graphics);
			float tickDelta = deltaTracker.getGameTimeDeltaPartialTick(false);
			try {
				for (ClientListener listener : this.listeners) {
					listener.onRender2D(renderer, tickDelta);
				}
			} finally {
				renderer.finish();
			}
		});

		// Once per frame on the client thread, while the game collects what to draw: gizmos may be added here.
		this.registerLevelExtraction(context -> {
			if (this.listeners.isEmpty()) {
				return;
			}

			GizmoRenderer3D renderer = new GizmoRenderer3D(context.camera());
			float tickDelta = context.deltaTracker().getGameTimeDeltaPartialTick(false);
			for (ClientListener listener : this.listeners) {
				listener.onRender3D(renderer, tickDelta);
			}
		});
	}

	@Override
	public Path gameDirectory() {
		return FabricLoader.getInstance().getGameDir();
	}

	@Override
	public void listen(ClientListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public InputBackend input() {
		return this.input;
	}

	@Override
	public AudioBackend audio() {
		return this.audio;
	}

	@Override
	public PacketAdapter packets() {
		return this.packets;
	}

	@Override
	public ScreenBackend screens() {
		return this.screens;
	}

	public CommonInput commonInput() {
		return this.input;
	}

	public CommonPackets commonPackets() {
		return this.packets;
	}

	// ---- what differs between versions ----

	/** Subscribes to the per-frame "level state extracted" event; its class moved between 26.1 and 26.2. */
	protected abstract void registerLevelExtraction(Consumer<LevelExtractionContext> handler);

	/** A GUI element for a plain coloured quad. The pipeline type lives in a different package from 26.3 on. */
	protected abstract GuiElementRenderState plainQuad(QuadGeometry geometry);

	/** A GUI element for a quarter-disc quad drawn with the client's own corner shader. */
	protected abstract GuiElementRenderState cornerQuad(QuadGeometry geometry);

	/** Key state by the game's native code (GLFW key or SDL scancode, whichever the version uses). */
	protected abstract boolean isNativeKeyDown(int code);

	protected abstract void setScreen(@Nullable Screen screen);

	protected abstract @Nullable Screen currentScreen();

	/** An empty clip region, for when nested scissors do not overlap. */
	static ScreenRectangle emptyRectangle() {
		return ScreenRectangle.empty();
	}
}
