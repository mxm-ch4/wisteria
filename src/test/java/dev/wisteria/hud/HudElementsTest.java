package dev.wisteria.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wisteria.modules.hud.ArrayListHud;
import dev.wisteria.modules.hud.Coordinates;
import dev.wisteria.modules.hud.Direction;
import dev.wisteria.modules.hud.Fps;
import dev.wisteria.modules.hud.Keybinds;
import dev.wisteria.modules.hud.Potions;
import dev.wisteria.modules.hud.TargetHud;
import dev.wisteria.modules.hud.Tps;
import dev.wisteria.modules.hud.TpsMeter;
import dev.wisteria.modules.watermark.Watermark;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** The elements' own logic, as pure functions, without a game. */
class HudElementsTest {
	@Test
	void coordinatesShowTheOtherDimension() {
		List<String[]> overworld = Coordinates.rows(800.7, 64.0, -160.2, "minecraft:overworld", true, false);
		assertEquals("800 64 -161", overworld.get(0)[1], "blocks are floored");
		assertEquals("Nether", overworld.get(1)[0]);
		assertEquals("100 64 -21", overworld.get(1)[1]);
		List<String[]> nether = Coordinates.rows(10.0, 70.0, -2.0, "minecraft:the_nether", true, true);
		assertEquals("Overworld", nether.get(1)[0]);
		assertEquals("80.0 70.0 -16.0", nether.get(1)[1]);
		assertEquals(1, Coordinates.rows(0, 0, 0, "minecraft:the_end", true, false).size(), "the End has no pair");
	}

	@Test
	void directionAndBiome() {
		assertEquals("South +Z", Direction.facing(0.0F));
		assertEquals("West -X", Direction.facing(90.0F));
		assertEquals("North -Z", Direction.facing(-180.0F));
		assertEquals("East +X", Direction.facing(-90.0F));
		assertEquals("South +Z", Direction.facing(719.0F));
		assertEquals("Dark Forest", Direction.prettyBiome("minecraft:dark_forest"));
	}

	@Test
	void fpsAndTpsLevels() {
		assertEquals(2, Fps.level(60));
		assertEquals(1, Fps.level(45));
		assertEquals(0, Fps.level(12));
		assertEquals(List.of("20.0", "tps", "2"), List.of(Tps.describe(20.0, 0.5)));
		assertEquals(List.of("16.3", "tps", "1"), List.of(Tps.describe(16.3, 0.5)));
		assertEquals(List.of("--", "tps", "1"), List.of(Tps.describe(Double.NaN, Double.NaN)));
		assertEquals(List.of("5s", "no ticks", "0"), List.of(Tps.describe(20.0, 5.2)));
	}

	@Test
	void tpsComesFromGameTimeOverRealTime() {
		TpsMeter meter = new TpsMeter();
		assertTrue(Double.isNaN(meter.tps()));
		long second = 1_000_000_000L;
		// A server at half speed: 20 ticks every two seconds.
		for (int i = 0; i <= 5; i++) {
			meter.record(1000L + i * 20L, i * 2L * second);
		}

		assertEquals(10.0, meter.tps(), 1e-9);
		assertEquals(3.0, meter.silence(13L * second), 1e-9);
		meter.record(10L, 14L * second);
		assertTrue(Double.isNaN(meter.tps()), "time going backwards starts over");
	}

	@Test
	void potionsAndKeys() {
		assertEquals("1:05", Potions.time(1300, false));
		assertEquals("0:00", Potions.time(-5, false));
		assertEquals("∞", Potions.time(0, true));
		assertEquals("", Potions.level(0));
		assertEquals(" II", Potions.level(1));
		assertEquals(" 12", Potions.level(11));
		assertEquals("RShift", Keybinds.keyLabel("right_shift"));
		assertEquals("G", Keybinds.keyLabel("g"));
		assertEquals("F12", Keybinds.keyLabel("f12"));
		assertEquals("Page up", Keybinds.keyLabel("page_up"));
	}

	@Test
	void arrayListWatermarkAndTarget() {
		assertEquals(List.of("FullBright", "ESP", "Fly"), ArrayListHud.order(List.of("ESP", "Fly", "FullBright"), String::length));
		assertEquals(List.of("ab", "ba"), ArrayListHud.order(List.of("ba", "ab"), String::length), "ties go by name");
		assertEquals("144 fps · 23 ms", Watermark.details(144, 23, true, true, ""));
		assertEquals("144 fps", Watermark.details(144, -1, true, true, ""), "unknown ping is left out");
		assertEquals("26.2", Watermark.details(144, 23, false, false, "26.2"));
		assertEquals(0.5F, TargetHud.fraction(10.0F, 20.0F));
		assertEquals(1.0F, TargetHud.fraction(30.0F, 20.0F));
	}

	@Test
	void toastsComeAndGo() {
		AtomicLong now = new AtomicLong();
		Toasts toasts = new Toasts(now::get);
		for (int i = 0; i < 6; i++) {
			toasts.push("T" + i, "", Toasts.Kind.INFO);
		}

		assertEquals(Toasts.MAX, toasts.shown().size(), "only the newest few");
		assertEquals(0.0F, toasts.shown().getFirst().presence(), "just pushed: about to slide in");
		now.addAndGet(Toasts.SHOW_NANOS / 2);
		assertEquals(1.0F, toasts.shown().getFirst().presence());
		now.addAndGet(Toasts.SHOW_NANOS);
		assertTrue(toasts.shown().isEmpty());
	}
}
