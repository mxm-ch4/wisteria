package dev.wisteria.gate.modules;

import static dev.wisteria.gate.GameTestKit.chat;
import static dev.wisteria.gate.GameTestKit.check;
import static dev.wisteria.gate.GameTestKit.grabLastFrame;

import dev.wisteria.Client;
import dev.wisteria.event.EventBus;
import dev.wisteria.event.Events;
import dev.wisteria.gate.GameTestKit;
import dev.wisteria.gate.GameTestKit.Frame;
import dev.wisteria.gate.ModuleGameTest;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ESP styles in a real world: a husk and a sheep in front of the camera, each style on its own and together,
 * chams in all three modes and behind a wall, and the cost with fifty entities in view.
 */
public final class EspGameTest extends ModuleGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("WisteriaGate");
	private static final int HOSTILE = 0xFFFF8A00;
	private static final int PASSIVE = 0xFF00E05A;

	@Override
	protected void test(ClientGameTestContext context, TestSingleplayerContext world, Client client) {
		GameTestKit.hideProbe(true);
		// GateTest set some ESP settings that its restart check looks for: put everything back at the end.
		java.util.Map<String, String> saved = context.computeOnClient(minecraft -> {
			java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
			client.modules().get("esp").settings().forEach(setting -> values.put(setting.id(), setting.format()));
			return values;
		});
		BlockPos feet = context.computeOnClient(minecraft -> minecraft.player.blockPosition());
		int x = feet.getX();
		int y = feet.getY();
		int z = feet.getZ();
		world.getServer().runCommand("time set noon");
		world.getServer().runCommand("tp @a " + (x + 0.5) + " " + y + " " + (z + 0.5) + " 0 15");
		world.getServer().runCommand("summon minecraft:husk " + (x + 1.8) + " " + y + " " + (z + 5.5) + " {NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[180f,0f]}");
		world.getServer().runCommand("summon minecraft:sheep " + (x - 0.8) + " " + y + " " + (z + 5.5) + " {NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[180f,0f],Color:0b}");
		// Colours no block or mob in the scene has, so the frame can be counted.
		chat(context, ".esp hostile-color #FFFF8A00");
		chat(context, ".esp passive-color #FF00E05A");
		chat(context, ".esp passive on");
		context.waitTicks(20);
		int[] none = this.count(context, client, "esp-off");
		chat(context, ".toggle esp on");

		int[] box = this.shot(context, client, "esp-box", "box on", "hitbox off", "corners off", "chams off");
		check("esp: Box draws both, husk and sheep, in their role colours (" + box[0] + ", " + box[1] + ")", box[0] > none[0] + 200 && box[1] > none[1] + 200);
		int[] hitbox = this.shot(context, client, "esp-hitbox", "box off", "hitbox on", "corners off", "chams off");
		check("esp: Hitbox alone (" + hitbox[0] + ", " + hitbox[1] + ")", hitbox[0] > none[0] + 60 && hitbox[1] > none[1] + 60);
		int[] corners = this.shot(context, client, "esp-corners", "box off", "hitbox off", "corners on", "chams off");
		check("esp: Corners alone, fewer pixels than a box (" + corners[0] + ")", corners[0] > none[0] + 30 && corners[0] < box[0]);
		int[] mixed = this.shot(context, client, "esp-hitbox-corners", "box off", "hitbox on", "corners on", "chams off");
		check("esp: Hitbox and Corners together (" + mixed[0] + ")", mixed[0] > hitbox[0] && mixed[0] > corners[0]);

		chat(context, ".esp chams-opacity 230");
		int[] flat = this.shot(context, client, "esp-chams-flat", "box off", "hitbox off", "corners off", "chams on", "chams-mode flat");
		check("esp: Chams flat fills the models (" + flat[0] + ", " + flat[1] + ")", flat[0] > none[0] + 800 && flat[1] > none[1] + 800);
		int[] rim = this.shot(context, client, "esp-chams-rim", "chams-mode rim");
		check("esp: Chams rim lights the silhouettes, less than a flat fill (" + rim[0] + ")", rim[0] > none[0] + 100 && rim[0] < flat[0]);
		int[] outline = this.shot(context, client, "esp-chams-outline", "chams-mode outline");
		check("esp: Chams outline draws the game's glow outline in the role colour (" + outline[0] + ")", outline[0] > none[0] + 100);
		int[] boxAndChams = this.shot(context, client, "esp-box-chams", "box on", "chams-mode flat");
		check("esp: Box and Chams together", boxAndChams[0] > flat[0]);

		// A stone wall between: the chams show through it only with Through Walls on.
		world.getServer().runCommand("fill " + (x - 4) + " " + (y - 1) + " " + (z + 3) + " " + (x + 4) + " " + (y + 4) + " " + (z + 3) + " minecraft:stone");
		context.waitTicks(10);
		int[] through = this.shot(context, client, "esp-chams-through-wall", "box off", "through-walls on");
		int[] hidden = this.shot(context, client, "esp-chams-behind-wall", "through-walls off");
		check("esp: through walls on shows the husk behind stone (" + through[0] + ")", through[0] > 800);
		check("esp: through walls off hides it (" + hidden[0] + ")", hidden[0] < 30);
		world.getServer().runCommand("fill " + (x - 4) + " " + (y - 1) + " " + (z + 3) + " " + (x + 4) + " " + (y + 4) + " " + (z + 3) + " minecraft:air");
		chat(context, ".esp through-walls on");

		this.fifty(context, world, client, x, y, z);

		chat(context, ".toggle esp off");
		saved.forEach((id, value) -> chat(context, ".esp " + id + " " + value));
		GameTestKit.hideProbe(false);
		check("esp: settings put back", context.computeOnClient(minecraft -> saved.equals(client.modules().get("esp").settings().stream()
			.collect(java.util.stream.Collectors.toMap(dev.wisteria.setting.Setting::id, dev.wisteria.setting.Setting::format, (a, b) -> a, java.util.LinkedHashMap::new)))));
	}

	private int[] shot(ClientGameTestContext context, Client client, String name, String... settings) {
		for (String setting : settings) {
			chat(context, ".esp " + setting);
		}

		context.waitTicks(8);
		return this.count(context, client, name);
	}

	/** Pixels near the hostile and the passive colour in the middle of the frame. */
	private int[] count(ClientGameTestContext context, Client client, String name) {
		Frame frame = grabLastFrame(context, client.platform(), name);
		int x0 = frame.width() / 5;
		int y0 = frame.height() / 8;
		int w = frame.width() * 3 / 5;
		int h = frame.height() * 3 / 4;
		int hostile = frame.count(x0, y0, w, h, argb -> GameTestKit.distance(argb, HOSTILE) < 70);
		int passive = frame.count(x0, y0, w, h, argb -> GameTestKit.distance(argb, PASSIVE) < 70);
		LOG.info("esp: {} hostile {} passive {}", name, hostile, passive);
		return new int[] {hostile, passive};
	}

	// Fifty sheep in view, every style on: what ESP itself costs per frame.
	private void fifty(ClientGameTestContext context, TestSingleplayerContext world, Client client, int x, int y, int z) {
		for (int i = 0; i < 50; i++) {
			world.getServer().runCommand("summon minecraft:sheep " + (x - 6 + i % 10 * 1.3) + " " + y + " " + (z + 9 + i / 10 * 1.6)
				+ " {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
		}

		context.waitTicks(20);
		long[] off = this.measure(context, client);
		for (String setting : List.of("box on", "hitbox on", "corners on", "chams on", "chams-mode flat")) {
			chat(context, ".esp " + setting);
		}

		context.waitTicks(10);
		long[] on = this.measure(context, client);
		grabLastFrame(context, client.platform(), "esp-fifty");
		int shown = context.computeOnClient(minecraft -> (int)minecraft.level.getEntitiesOfClass(net.minecraft.world.entity.animal.sheep.Sheep.class,
			minecraft.player.getBoundingBox().inflate(40)).size());
		LOG.info("esp: {} sheep; frame {} us without styles, {} us with all four; ESP itself {} us per frame", shown, off[0], on[0], on[1]);
		check("esp: fifty entities with every style cost ESP under 2 ms of CPU per frame (" + on[1] + " us)", shown >= 50 && on[1] < 2000);
	}

	/** {average frame interval, average time in ESP's own Render3D handler}, in microseconds. */
	private long[] measure(ClientGameTestContext context, Client client) {
		long[] state = new long[5];
		EventBus.Subscription<Events.Render3D> timer = client.bus().subscribe(Events.Render3D.class, event -> {
			long now = System.nanoTime();
			if (state[1] != 0L) {
				state[0]++;
				state[2] += now - state[1];
			}

			state[1] = now;
		});
		context.waitTicks(40);
		context.runOnClient(minecraft -> client.bus().unsubscribe(timer));
		long frames = Math.max(1L, state[0]);
		// ESP's own cost: run its handler directly under a stopwatch, the same frame data, a hundred times.
		long cost = context.computeOnClient(minecraft -> {
			dev.wisteria.modules.esp.EntityEsp esp = client.modules().get(dev.wisteria.modules.esp.EntityEsp.class);
			if (!esp.isEnabled()) {
				return 0L;
			}

			Counting renderer = new Counting();
			long start = System.nanoTime();
			for (int i = 0; i < 100; i++) {
				client.bus().post(new Events.Render3D(renderer, 1.0F));
			}

			return (System.nanoTime() - start) / 100L / 1000L;
		});
		return new long[] {state[2] / frames / 1000L, cost};
	}

	/** A renderer that only counts, so timing ESP measures ESP, not the game's gizmos. */
	private static final class Counting implements dev.wisteria.render.Renderer3D {
		int calls;

		@Override
		public double cameraX() {
			return net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera().position().x;
		}

		@Override
		public double cameraY() {
			return net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera().position().y;
		}

		@Override
		public double cameraZ() {
			return net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera().position().z;
		}

		@Override
		public float cameraForwardX() {
			return 0;
		}

		@Override
		public float cameraForwardY() {
			return 0;
		}

		@Override
		public float cameraForwardZ() {
			return 1;
		}

		@Override
		public void box(double a, double b, double c, double d, double e, double f, int g, float h, int i, boolean j) {
			this.calls++;
		}

		@Override
		public void boxes(dev.wisteria.render.BoxBatch batch) {
			this.calls++;
		}

		@Override
		public void line(double a, double b, double c, double d, double e, double f, int g, float h, boolean i) {
			this.calls++;
		}
	}
}
