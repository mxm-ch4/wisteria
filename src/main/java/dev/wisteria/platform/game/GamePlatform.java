package dev.wisteria.platform.game;

import dev.wisteria.platform.AudioBackend;
import dev.wisteria.platform.ChatBackend;
import dev.wisteria.platform.ClientListener;
import dev.wisteria.platform.InputBackend;
import dev.wisteria.platform.PacketAdapter;
import dev.wisteria.platform.Platform;
import dev.wisteria.platform.ScreenBackend;
import dev.wisteria.platform.VisualBackend;
import dev.wisteria.platform.WorldBackend;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The part of the platform that compiles unchanged against every supported Minecraft version.
 * A version subclass fills in what differs: render pipelines, the level extraction event, key state, screens.
 */
public final class GamePlatform implements Platform {
	private static @Nullable GamePlatform instance;

	private final List<ClientListener> listeners = new CopyOnWriteArrayList<>();
	private final GameInput input = new GameInput(this);
	private final GameAudio audio = new GameAudio();
	private final GamePackets packets = new GamePackets();
	private final GameScreens screens = new GameScreens(this);
	private final GameChat chat = new GameChat(this);
	private final GameWorld world = new GameWorld();
	private final GameVisuals visuals = new GameVisuals();
	private final BoxBatchRenderer batches = new BoxBatchRenderer();
	private boolean titleScreenSeen;

	/** The installed platform, or null before the mod initialises. Mixins go through this. */
	public static @Nullable GamePlatform instance() {
		return instance;
	}

	/** Hooks the game events. Called once by the version entrypoint. */
	public final void install() {
		instance = this;
		this.chat.install();
		GameEvents.install();

		ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> {
			for (ClientListener listener : this.listeners) {
				listener.onStop();
			}
		});

		ClientTickEvents.START_CLIENT_TICK.register(minecraft -> {
			for (ClientListener listener : this.listeners) {
				listener.onTickStart();
			}
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> {
			for (ClientListener listener : this.listeners) {
				listener.onJoin();
			}
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> {
			for (ClientListener listener : this.listeners) {
				listener.onLeave();
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (!this.titleScreenSeen && this.currentScreen() instanceof TitleScreen) {
				this.titleScreenSeen = true;
				for (ClientListener listener : this.listeners) {
					listener.onTitleScreen();
				}
			}

			this.screens.tick();
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

		// Batches queued during extraction are drawn at the end of the world's main pass, over terrain and entities.
		net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.END_MAIN.register(context ->
			this.batches.draw(context.levelState().cameraRenderState.pos));

		// Once per frame on the client thread, while the game collects what to draw: gizmos may be added here.
		this.registerLevelExtraction(context -> {
			if (this.listeners.isEmpty()) {
				return;
			}

			GizmoRenderer3D renderer = new GizmoRenderer3D(context.camera(), this.batches);
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
	public void execute(Runnable task) {
		Minecraft.getInstance().execute(task);
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

	@Override
	public ChatBackend chat() {
		return this.chat;
	}

	@Override
	public WorldBackend world() {
		return this.world;
	}

	@Override
	public VisualBackend visuals() {
		return this.visuals;
	}

	public GameChat gameChat() {
		return this.chat;
	}

	public GameVisuals gameVisuals() {
		return this.visuals;
	}

	public GameInput gameInput() {
		return this.input;
	}

	public GamePackets gamePackets() {
		return this.packets;
	}

	/** The client's rounded-box shader; shape parameters travel in the ENTITY vertex format (see QuadGeometry). */
	private static final RenderPipeline PANEL = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
		.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
		.withLocation(Identifier.fromNamespaceAndPath("wisteria", "pipeline/panel"))
		.withVertexShader(Identifier.fromNamespaceAndPath("wisteria", "core/panel"))
		.withFragmentShader(Identifier.fromNamespaceAndPath("wisteria", "core/panel"))
		.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
		.withVertexBinding(0, DefaultVertexFormat.ENTITY)
		.withPrimitiveTopology(PrimitiveTopology.QUADS)
		.withCull(false)
		.build();

	@Override
	public void leaveWorld() {
		net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
		if (minecraft.level != null) {
			minecraft.disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
		}
	}

	@Override
	public void quitGame() {
		net.minecraft.client.Minecraft.getInstance().stop();
	}

	@Override
	public void writeNetworkAudit() {
		try {
			java.nio.file.Path file = SessionAudit.write(this.gameDirectory().resolve(dev.wisteria.Wisteria.ID));
			this.chat.print("Network audit written: " + file.getFileName());
			net.minecraft.util.Util.getPlatform().openPath(file.getParent());
		} catch (java.io.IOException e) {
			this.chat.print("Could not write the network audit: " + e.getMessage());
		}
	}

	@Override
	public String clientVersion() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(dev.wisteria.Wisteria.ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("dev");
	}

	@Override
	public String minecraftVersion() {
		return "26.2";
	}

	@Override
	public String graphicsBackend() {
		return RenderSystem.getDevice().getDeviceInfo().backendName();
	}

	/** Subscribes to the per-frame "level state extracted" event. */
	void registerLevelExtraction(Consumer<LevelExtractionContext> handler) {
		LevelExtractionEvents.END_EXTRACTION.register(handler::accept);
	}

	/** A GUI element for a plain coloured quad. */
	GuiElementRenderState plainQuad(QuadGeometry geometry) {
		return new QuadState(RenderPipelines.GUI, geometry);
	}

	/** A GUI element for a rounded box drawn with the client's panel shader (see {@link QuadGeometry}). */
	GuiElementRenderState panelQuad(QuadGeometry geometry) {
		return new QuadState(PANEL, geometry);
	}

	/** Key state by GLFW key code. */
	boolean isNativeKeyDown(int code) {
		return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), code);
	}

	void setScreen(@Nullable Screen screen) {
		Minecraft.getInstance().gui.setScreen(screen);
	}

	@Nullable Screen currentScreen() {
		return Minecraft.getInstance().gui.screen();
	}

	ChatComponent chatComponent() {
		return Minecraft.getInstance().gui.hud.getChat();
	}

	/** An empty clip region, for when nested scissors do not overlap. */
	static ScreenRectangle emptyRectangle() {
		return ScreenRectangle.empty();
	}
}
