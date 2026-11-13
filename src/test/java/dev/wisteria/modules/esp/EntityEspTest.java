package dev.wisteria.modules.esp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wisteria.Client;
import dev.wisteria.FakePlatform;
import dev.wisteria.event.Events;
import dev.wisteria.platform.WorldBackend;
import dev.wisteria.render.BoxBatch;
import dev.wisteria.render.Renderer3D;
import dev.wisteria.social.Relations;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntityEspTest {
	@TempDir
	Path game;

	private FakePlatform platform;
	private Client client;
	private EntityEsp esp;

	/** Counts what ESP draws. */
	private static final class Recorder implements Renderer3D {
		final List<Integer> boxColours = new ArrayList<>();
		final List<Integer> lineColours = new ArrayList<>();
		final List<Boolean> walls = new ArrayList<>();

		@Override
		public double cameraX() {
			return 0;
		}

		@Override
		public double cameraY() {
			return 64;
		}

		@Override
		public double cameraZ() {
			return 0;
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
		public void box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int strokeArgb, float strokeWidth, int fillArgb, boolean throughWalls) {
			this.boxColours.add(strokeArgb);
			this.walls.add(throughWalls);
		}

		@Override
		public void boxes(BoxBatch batch) {
		}

		@Override
		public void line(double x1, double y1, double z1, double x2, double y2, double z2, int argb, float width, boolean throughWalls) {
			this.lineColours.add(argb);
			this.walls.add(throughWalls);
		}
	}

	@BeforeEach
	void start() {
		this.platform = new FakePlatform(this.game);
		this.client = new Client(this.platform);
		this.esp = this.client.modules().get(EntityEsp.class);
		this.platform.entities.add(new WorldBackend.Entity(1, WorldBackend.EntityKind.PLAYER, "Popbob", 3, 64, 0, 0.6F, 1.8F, false));
		this.platform.entities.add(new WorldBackend.Entity(2, WorldBackend.EntityKind.HOSTILE, "Zombie", 0, 64, 4, 0.6F, 1.95F, false));
		this.platform.entities.add(new WorldBackend.Entity(3, WorldBackend.EntityKind.PASSIVE, "Sheep", -3, 64, 0, 0.9F, 1.3F, false));
		this.platform.entities.add(new WorldBackend.Entity(4, WorldBackend.EntityKind.PLAYER, "Me", 0, 64, 0, 0.6F, 1.8F, true));
		this.platform.entities.add(new WorldBackend.Entity(5, WorldBackend.EntityKind.HOSTILE, "Far", 0, 64, 500, 0.6F, 1.95F, false));
	}

	private Recorder frame() {
		Recorder recorder = new Recorder();
		this.client.bus().post(new Events.Render3D(recorder, 1.0F));
		return recorder;
	}

	@Test
	void defaultsAreABoxThroughWalls() {
		assertEquals("on", this.esp.setting("box").format());
		for (String style : List.of("hitbox", "corners", "chams")) {
			assertEquals("off", this.esp.setting(style).format(), style);
		}

		assertEquals("on", this.esp.setting("through-walls").format());
		this.platform.typeInChat(".esp");
		Recorder frame = this.frame();
		// Popbob and the zombie; the sheep (passive off by default), the player themself and the far one are not.
		assertEquals(2, frame.boxColours.size());
		assertTrue(frame.lineColours.isEmpty());
		assertTrue(frame.walls.stream().allMatch(on -> on));
	}

	@Test
	void stylesCombineFreely() {
		this.platform.typeInChat(".esp");
		this.platform.typeInChat(".esp box off");
		this.platform.typeInChat(".esp corners on");
		assertEquals(2 * 24, this.frame().lineColours.size(), "only corners: 24 strokes each");
		this.platform.typeInChat(".esp hitbox on");
		Recorder both = this.frame();
		assertEquals(2, both.boxColours.size(), "the hitbox outline");
		assertEquals(2 * (24 + 4 + 1), both.lineColours.size(), "corners, eye level, gaze");
		this.platform.typeInChat(".esp hitbox-gaze off");
		this.platform.typeInChat(".esp box on");
		this.platform.typeInChat(".esp through-walls off");
		Recorder all = this.frame();
		assertEquals(4, all.boxColours.size());
		assertEquals(2 * (24 + 4), all.lineColours.size());
		assertTrue(all.walls.stream().noneMatch(on -> on), "one switch for every style");
	}

	@Test
	void coloursFollowWhoItIs() {
		this.platform.typeInChat(".esp");
		this.platform.typeInChat(".esp passive on");
		this.platform.typeInChat(".friend add Popbob");
		Recorder frame = this.frame();
		assertTrue(frame.boxColours.contains(this.esp.colorOf(EspGeometry.Role.FRIEND)));
		assertTrue(frame.boxColours.contains(this.esp.colorOf(EspGeometry.Role.HOSTILE)));
		assertTrue(frame.boxColours.contains(this.esp.colorOf(EspGeometry.Role.PASSIVE)));
		this.platform.typeInChat(".friend remove Popbob");
		this.platform.typeInChat(".enemy add Popbob");
		assertTrue(this.frame().boxColours.contains(this.esp.colorOf(EspGeometry.Role.ENEMY)));
		this.platform.typeInChat(".esp players off");
		assertFalse(this.frame().boxColours.contains(this.esp.colorOf(EspGeometry.Role.ENEMY)));
	}

	@Test
	void containersAreOptional() {
		this.platform.containers.add(new WorldBackend.Container(2, 64, 2, "minecraft:chest"));
		this.platform.typeInChat(".esp");
		assertFalse(this.frame().boxColours.contains(this.esp.colorOf(EspGeometry.Role.CONTAINER)));
		this.platform.typeInChat(".esp containers on");
		assertTrue(this.frame().boxColours.contains(this.esp.colorOf(EspGeometry.Role.CONTAINER)));
	}

	@Test
	void chamsColourFollowsTheFilters() {
		assertEquals(0, this.esp.chamsColor(WorldBackend.EntityKind.HOSTILE, "Zombie", false, 5.0), "module off");
		this.platform.typeInChat(".esp");
		assertEquals(0, this.esp.chamsColor(WorldBackend.EntityKind.HOSTILE, "Zombie", false, 5.0), "chams off");
		this.platform.typeInChat(".esp chams on");
		this.platform.typeInChat(".esp chams-opacity 200");
		int colour = this.esp.chamsColor(WorldBackend.EntityKind.HOSTILE, "Zombie", false, 5.0);
		assertEquals(200, colour >>> 24);
		assertEquals(this.esp.colorOf(EspGeometry.Role.HOSTILE) & 0xFFFFFF, colour & 0xFFFFFF);
		assertEquals(0, this.esp.chamsColor(WorldBackend.EntityKind.HOSTILE, "Zombie", false, 300.0), "out of range");
		assertEquals(0, this.esp.chamsColor(WorldBackend.EntityKind.PLAYER, "Me", true, 0.0), "never yourself");
		assertEquals(0, this.esp.chamsColor(WorldBackend.EntityKind.PASSIVE, "Sheep", false, 3.0), "passive filtered out");
		this.platform.typeInChat(".esp chams-mode outline");
		assertEquals(EntityEsp.ChamsMode.OUTLINE, this.esp.chamsMode());
	}

	@Test
	void settingsSurviveARestart() {
		this.platform.typeInChat(".esp corners on");
		this.platform.typeInChat(".esp chams on");
		this.platform.typeInChat(".esp chams-mode rim");
		this.platform.typeInChat(".esp corner-length 0.4");
		this.platform.stop();
		EntityEsp after = new Client(new FakePlatform(this.game)).modules().get(EntityEsp.class);
		assertEquals("on", after.setting("corners").format());
		assertEquals(EntityEsp.ChamsMode.RIM, after.chamsMode());
		assertEquals("0.4", after.setting("corner-length").format());
	}

	@Test
	void geometry() {
		List<double[]> corners = EspGeometry.corners(0, 0, 0, 1, 2, 1, 0.25);
		assertEquals(24, corners.size());
		double[] first = corners.getFirst();
		assertEquals(0.25, first[3] - first[0], 1e-9, "a quarter of the 1-block edge");
		assertEquals(0.5, corners.get(1)[4] - corners.get(1)[1], 1e-9, "a quarter of the 2-block edge");
		double[] look = EspGeometry.lookLine(0, 1.6, 0, 0, 0, 5, 2.0);
		assertEquals(2.0, look[5], 1e-9, "normalised, two blocks long");
		assertEquals(EspGeometry.Role.FRIEND, EspGeometry.role(WorldBackend.EntityKind.PLAYER, Relations.Relation.FRIEND));
		assertNull(EspGeometry.role(WorldBackend.EntityKind.OTHER, Relations.Relation.NEUTRAL));
		assertEquals(120, EspGeometry.pulse(120, false, 123L));
		int low = Integer.MAX_VALUE;
		int high = 0;
		for (long t = 0; t < 1500; t += 10) {
			low = Math.min(low, EspGeometry.pulse(100, true, t));
			high = Math.max(high, EspGeometry.pulse(100, true, t));
		}

		assertEquals(40, low);
		assertEquals(100, high);
	}
}
