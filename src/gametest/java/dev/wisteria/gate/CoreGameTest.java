package dev.wisteria.gate;

import static dev.wisteria.gate.GameTestKit.check;

import dev.wisteria.Client;
import dev.wisteria.Wisteria;
import dev.wisteria.event.EventBus;
import dev.wisteria.event.Events;
import dev.wisteria.render.BoxBatch;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The core's events in a real world: each one fires, on the client thread, with the data docs/module-api.md
 * promises. Then the 3D renderer under the Xray load of 10 000 boxes.
 */
public final class CoreGameTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("WisteriaGate");
	private final List<Object> seen = new CopyOnWriteArrayList<>();
	private final List<String> offThread = new CopyOnWriteArrayList<>();
	private final List<EventBus.Subscription<?>> subscriptions = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (GameTestKit.isRestartPhase()) {
			return;
		}

		Client client = Wisteria.client();
		context.runOnClient(minecraft -> {
			for (Class<?> type : List.of(Events.ChunkLoad.class, Events.ChunkUnload.class, Events.BlockUpdate.class,
				Events.EntityAdd.class, Events.EntityRemove.class, Events.ContainerOpen.class, Events.ContainerContent.class,
				Events.ChatReceive.class, Events.Death.class, Events.Respawn.class, Events.ScreenOpen.class,
				Events.ScreenClose.class, Events.Disconnect.class)) {
				this.record(client, type);
			}
		});

		TestSingleplayerContext world = GameTestKit.newWorld(context);
		this.events(context, world, client);
		this.boxes(context, client);
		this.behindAWall(context, world, client);
		this.bench(context, client);
		this.kicked(context, world);
		try {
			world.close();
		} catch (IllegalStateException expected) {
			// The kick has already left the world and stopped its server; the context only complains about that.
		}

		context.setScreen(net.minecraft.client.gui.screens.TitleScreen::new);
		context.waitTicks(5);

		context.runOnClient(minecraft -> this.subscriptions.forEach(client.bus()::unsubscribe));
		check("events: all on the client thread " + this.offThread, this.offThread.isEmpty());
	}

	private <E> void record(Client client, Class<E> type) {
		this.subscriptions.add(client.bus().subscribe(type, event -> {
			if (!Thread.currentThread().getName().equals("Render thread")) {
				this.offThread.add(type.getSimpleName() + " on " + Thread.currentThread().getName());
			}

			this.seen.add(event);
		}));
	}

	// The chat keeps its messages private; reading them is fine for a test.
	@SuppressWarnings("unchecked")
	private static List<String> chatLines(net.minecraft.client.Minecraft minecraft) {
		try {
			java.lang.reflect.Field field = net.minecraft.client.gui.components.ChatComponent.class.getDeclaredField("allMessages");
			field.setAccessible(true);
			List<net.minecraft.client.multiplayer.chat.GuiMessage> messages = (List<net.minecraft.client.multiplayer.chat.GuiMessage>)field.get(minecraft.gui.hud.getChat());
			return messages.stream().map(message -> message.content().getString()).toList();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private <E> boolean any(Class<E> type, Predicate<E> test) {
		return this.seen.stream().filter(type::isInstance).map(type::cast).anyMatch(test);
	}

	private void events(ClientGameTestContext context, TestSingleplayerContext world, Client client) {
		check("events: ChunkLoad fires for the chunks around the player", this.any(Events.ChunkLoad.class, event -> event.chunk().getPos() != null));

		BlockPos feet = context.computeOnClient(minecraft -> minecraft.player.blockPosition());
		BlockPos stone = feet.offset(3, 0, 0);
		world.getServer().runCommand("setblock " + stone.getX() + " " + stone.getY() + " " + stone.getZ() + " minecraft:stone");
		context.waitTicks(5);
		check("events: BlockUpdate carries the position and both states",
			this.any(Events.BlockUpdate.class, event -> event.pos().equals(stone) && event.newState().is(Blocks.STONE) && !event.oldState().is(Blocks.STONE)));

		world.getServer().runCommand("summon minecraft:pig " + feet.getX() + " " + feet.getY() + " " + (feet.getZ() + 4) + " {NoAI:1b,Tags:[\"probe\"]}");
		context.waitTicks(10);
		check("events: EntityAdd fires for a summoned pig", this.any(Events.EntityAdd.class, event -> EntityType.getKey(event.entity().getType()).getPath().equals("pig")));
		world.getServer().runCommand("kill @e[tag=probe]");
		context.waitTicks(30);
		check("events: EntityRemove fires once it is gone", this.any(Events.EntityRemove.class, event -> EntityType.getKey(event.entity().getType()).getPath().equals("pig")));

		world.getServer().runCommand("tellraw @a {\"text\":\"wisteria core probe\"}");
		context.waitTicks(5);
		check("events: ChatReceive gets a system message as text",
			this.any(Events.ChatReceive.class, event -> event.system() && event.text().equals("wisteria core probe")));
		EventBus.Subscription<Events.ChatReceive> hide = client.bus().subscribe(Events.ChatReceive.class, event -> {
			if (event.text().contains("hide me")) {
				event.cancel();
			}
		});
		world.getServer().runCommand("tellraw @a {\"text\":\"please hide me\"}");
		context.waitTicks(5);
		client.bus().unsubscribe(hide);
		boolean shown = context.computeOnClient(minecraft -> chatLines(minecraft).stream().anyMatch(line -> line.contains("hide me")));
		boolean probeShown = context.computeOnClient(minecraft -> chatLines(minecraft).stream().anyMatch(line -> line.contains("core probe")));
		check("events: an uncancelled message reaches the chat", probeShown);
		check("events: a cancelled ChatReceive stays out of the chat", !shown);

		// Containers: a real right click on a chest with diamonds in it.
		BlockPos chest = feet.offset(0, 0, 2);
		world.getServer().runCommand("setblock " + chest.getX() + " " + chest.getY() + " " + chest.getZ() + " minecraft:chest");
		world.getServer().runCommand("item replace block " + chest.getX() + " " + chest.getY() + " " + chest.getZ() + " container.0 with minecraft:diamond 5");
		context.waitTicks(5);
		context.runOnClient(minecraft -> minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(chest), Direction.UP, chest, false)));
		context.waitTicks(10);
		check("events: ContainerOpen knows the chest's position and menu",
			this.any(Events.ContainerOpen.class, event -> chest.equals(event.pos()) && event.menu().equals("minecraft:generic_9x3")));
		check("events: ContainerContent has the chest's 27 slots with the diamonds",
			this.any(Events.ContainerContent.class, event -> chest.equals(event.pos()) && event.containerSlots() == 27
				&& event.containerItems().get(0).is(Items.DIAMOND) && event.containerItems().get(0).getCount() == 5));
		context.runOnClient(minecraft -> minecraft.player.closeContainer());
		context.waitTicks(5);
		check("events: ScreenOpen and ScreenClose around the chest screen",
			this.any(Events.ScreenOpen.class, event -> event.screen().getClass().getSimpleName().equals("ContainerScreen"))
				&& this.any(Events.ScreenClose.class, event -> event.screen().getClass().getSimpleName().equals("ContainerScreen")));

		// A cancelled ScreenOpen keeps the screen shut.
		EventBus.Subscription<Events.ScreenOpen> veto = client.bus().subscribe(Events.ScreenOpen.class, event -> {
			if (event.screen() instanceof InventoryScreen) {
				event.cancel();
			}
		});
		context.runOnClient(minecraft -> minecraft.gui.setScreen(new InventoryScreen(minecraft.player)));
		context.waitTicks(2);
		boolean opened = context.computeOnClient(minecraft -> minecraft.gui.screen() instanceof InventoryScreen);
		client.bus().unsubscribe(veto);
		check("events: a cancelled ScreenOpen keeps the screen closed", !opened);

		world.getServer().runCommand("kill @a");
		context.waitTicks(10);
		check("events: Death fires with where it happened", this.any(Events.Death.class, event -> event.position().distanceTo(Vec3.atCenterOf(feet)) < 3.0
			&& event.dimension().equals("minecraft:overworld")));
		context.runOnClient(minecraft -> minecraft.player.respawn());
		context.waitTicks(20);
		check("events: Respawn fires after respawning", this.any(Events.Respawn.class, event -> true));
		context.runOnClient(minecraft -> minecraft.gui.setScreen(null));
		context.waitTicks(5);
	}

	// The server drops the player: the one case with a reason. Leaving on your own fires only Leave.
	private void kicked(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().connection
			.disconnect(net.minecraft.network.chat.Component.literal("wisteria probe kick")));
		// Under load the client can take a while to notice; wait for the event rather than a fixed time.
		context.waitFor(minecraft -> this.any(Events.Disconnect.class, event -> true), 600);
		// The event fires first; then the game leaves the world and shows its "disconnected" screen.
		context.waitFor(minecraft -> minecraft.level == null
			&& GateHooks.currentScreen(minecraft) instanceof net.minecraft.client.gui.screens.DisconnectedScreen, 600);
		check("events: Disconnect carries the server's reason", this.any(Events.Disconnect.class, event -> event.text().equals("wisteria probe kick")));
	}

	// The Xray load: 10 000 boxes with outline and fill, through walls, around the camera; one by one and as a batch.
	private void boxes(ClientGameTestContext context, Client client) {
		Vec3 camera = context.computeOnClient(minecraft -> minecraft.player.getEyePosition());
		BoxBatch batch = new BoxBatch(1.5F, true);
		forGrid(camera, 1.0, (x, y, z, size) -> batch.add(x, y, z, x + size, y + size, z + size, 0xFFB79CFF, 0x40B79CFF));
		this.measure(context, client, renderer -> forGrid(camera, 1.0, (x, y, z, size) ->
			renderer.box(x, y, z, x + size, y + size, z + size, 0xFFB79CFF, 1.5F, 0x40B79CFF, true)));
		GameTestKit.Frame single = GameTestKit.grabLastFrame(context, client.platform(), "core-boxes-single");
		Stats batched = this.measure(context, client, renderer -> renderer.boxes(batch));
		GameTestKit.Frame drawn = GameTestKit.grabLastFrame(context, client.platform(), "core-boxes-batch");
		check("render: a 10 000-box batch costs under 0.2 ms of CPU to submit (" + batched.submitMicros + " us)", batched.submitMicros < 200);
		// Above the chat and the hand, which change between the two frames on their own.
		int compared = single.width() * (single.height() * 7 / 10);
		int differing = 0;
		for (int i = 0; i < compared; i++) {
			differing += GameTestKit.distance(single.pixels()[i], drawn.pixels()[i]) > 32 ? 1 : 0;
		}

		check("render: the batch draws what single boxes draw (" + differing + " of " + compared + " pixels differ)",
			drawn.count(0, 0, drawn.width(), drawn.height(), GameTestKit::isViolet) > drawn.width() * drawn.height() / 4
				&& differing < compared / 100);

		// Frame time: the software GPU of the headless gate spends most of it filling pixels, which a real GPU
		// does in no time; tiny boxes leave only the per-box work, which is what the batch removes.
		BoxBatch tiny = new BoxBatch(1.5F, true);
		forGrid(camera, 0.02, (x, y, z, size) -> tiny.add(x, y, z, x + size, y + size, z + size, 0xFFB79CFF, 0x40B79CFF));
		Stats none = this.measure(context, client, renderer -> {
		});
		Stats tinySingle = this.measure(context, client, renderer -> forGrid(camera, 0.02, (x, y, z, size) ->
			renderer.box(x, y, z, x + size, y + size, z + size, 0xFFB79CFF, 1.5F, 0x40B79CFF, true)));
		Stats tinyBatch = this.measure(context, client, renderer -> renderer.boxes(tiny));
		long singleCost = tinySingle.frameMicros - none.frameMicros;
		long batchCost = tinyBatch.frameMicros - none.frameMicros;
		LOG.info("render: no boxes {}; full-size batch {}; tiny one by one {}; tiny batch {}", none, batched, tinySingle, tinyBatch);
		// What is left for the batch here is the software GPU transforming vertices; on a real GPU that is free.
		check("render: per-box frame cost of a batch is under 60% of single boxes (" + batchCost + " vs " + singleCost + " us)",
			batchCost * 10 < singleCost * 6);
	}

	// A batch box behind a stone wall: seen through it with throughWalls on, hidden by it with it off.
	private void behindAWall(ClientGameTestContext context, TestSingleplayerContext world, Client client) {
		BlockPos feet = context.computeOnClient(minecraft -> minecraft.player.blockPosition());
		int x = feet.getX();
		int y = feet.getY();
		int z = feet.getZ();
		// Facing +Z, looking straight ahead; the wall 3 blocks in front, the box 3 blocks behind it at eye height.
		world.getServer().runCommand("tp @a " + (x + 0.5) + " " + y + " " + (z + 0.5) + " 0 0");
		world.getServer().runCommand("fill " + (x - 4) + " " + (y - 1) + " " + (z + 3) + " " + (x + 4) + " " + (y + 4) + " " + (z + 3) + " minecraft:stone");
		context.waitTicks(10);
		BoxBatch batch = new BoxBatch(2.0F, true);
		batch.add(x, y + 1, z + 6, x + 1, y + 2, z + 7, 0xFFB79CFF, 0xE0B79CFF);
		EventBus.Subscription<Events.Render3D> draw = client.bus().subscribe(Events.Render3D.class, event -> event.renderer().boxes(batch));
		context.waitTicks(5);
		GameTestKit.Frame through = GameTestKit.grabLastFrame(context, client.platform(), "core-wall-through");
		context.runOnClient(minecraft -> batch.setThroughWalls(false));
		context.waitTicks(5);
		GameTestKit.Frame hidden = GameTestKit.grabLastFrame(context, client.platform(), "core-wall-hidden");
		context.runOnClient(minecraft -> client.bus().unsubscribe(draw));
		int seen = violetAroundCentre(through);
		int behind = violetAroundCentre(hidden);
		check("render: a batch with throughWalls shows through the wall (" + seen + " violet pixels)", seen > 400);
		check("render: a batch without throughWalls is hidden by the wall (" + behind + " violet pixels)", behind < 10);
		world.getServer().runCommand("fill " + (x - 4) + " " + (y - 1) + " " + (z + 3) + " " + (x + 4) + " " + (y + 4) + " " + (z + 3) + " minecraft:air");
	}

	// Just the box's own square on screen: earlier gate tests leave violet HUD panels to the left of it.
	private static int violetAroundCentre(GameTestKit.Frame frame) {
		int size = frame.height() / 16;
		return frame.count(frame.width() / 2 - size, frame.height() / 2 - size, size * 2, size * 2, GameTestKit::isViolet);
	}

	// What the owner runs on their own card: .bench boxes 10000 reports once, after 5 seconds.
	private void bench(ClientGameTestContext context, Client client) {
		GameTestKit.chat(context, ".bench boxes 10000");
		// Five seconds of frames; ticks run slower than real time under the headless software GPU.
		context.waitFor(minecraft -> chatLines(minecraft).stream().anyMatch(line -> line.contains("Bench: 10000 boxes,")), 1200);
		context.waitTicks(20);
		List<String> lines = context.computeOnClient(CoreGameTest::chatLines);
		List<String> results = lines.stream().filter(line -> line.contains("Bench: 10000 boxes,")).toList();
		LOG.info("render: {}", results);
		check("render: .bench boxes 10000 reports average fps and the worst frame once",
			results.size() == 1 && results.getFirst().contains("fps average, worst frame"));
	}

	private interface Cell {
		void at(double x, double y, double z, double size);
	}

	// 25 x 16 x 25 cells every other block around the camera: boxes in every direction, some behind it.
	private static void forGrid(Vec3 camera, double size, Cell cell) {
		for (int i = 0; i < 10_000; i++) {
			cell.at(Math.floor(camera.x) + (i % 25 - 12) * 2, Math.floor(camera.y) + (i / 625 - 8) * 2, Math.floor(camera.z) + (i / 25 % 25 - 12) * 2, size);
		}
	}

	private Stats measure(ClientGameTestContext context, Client client, Consumer<dev.wisteria.render.Renderer3D> draw) {
		long[] state = new long[4]; // frames, last frame start, sum of intervals, sum of submit time
		EventBus.Subscription<Events.Render3D> subscription = client.bus().subscribe(Events.Render3D.class, event -> {
			long start = System.nanoTime();
			draw.accept(event.renderer());
			long end = System.nanoTime();
			if (state[1] != 0L) {
				state[0]++;
				state[2] += start - state[1];
				state[3] += end - start;
			}

			state[1] = start;
		});
		context.waitTicks(20);
		context.runOnClient(minecraft -> {
			state[0] = 0;
			state[2] = 0;
			state[3] = 0;
		});
		context.waitTicks(60);
		Stats stats = context.computeOnClient(minecraft -> new Stats(state[0], state[0] == 0 ? 0 : state[2] / state[0] / 1000, state[0] == 0 ? 0 : state[3] / state[0] / 1000));
		context.runOnClient(minecraft -> client.bus().unsubscribe(subscription));
		return stats;
	}

	private record Stats(long frames, long frameMicros, long submitMicros) {
		@Override
		public String toString() {
			return this.frames + " frames, " + this.frameMicros + " us per frame, " + this.submitMicros + " us to submit";
		}
	}
}
