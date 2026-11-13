package dev.wisteria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wisteria.ui.Animation;
import dev.wisteria.ui.Colors;
import dev.wisteria.ui.Easing;
import dev.wisteria.ui.Theme;
import dev.wisteria.ui.ThemeManager;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DesignSystemTest {
	@Test
	void colourArithmetic() {
		assertEquals(0xFFB79CFF, Colors.argb(255, 0xB7, 0x9C, 0xFF));
		assertEquals(0x80B79CFF, Colors.withAlpha(0xFFB79CFF, 0x80));
		assertEquals(0x40B79CFF, Colors.fade(0x80B79CFF, 0.5F));
		assertEquals(0xFF000000, Colors.mix(0xFF000000, 0xFFFFFFFF, 0.0F));
		assertEquals(0xFF808080, Colors.mix(0xFF000000, 0xFFFFFFFF, 0.5F));
		assertEquals(0xFFFFFFFF, Colors.mix(0xFF000000, 0xFFFFFFFF, 7.0F));
		assertEquals(0x80FFFFFF, Colors.lighten(0x80000000, 1.0F), "lightening keeps the alpha");
		assertTrue(Colors.luminance(0xFFFFFFFF) > 0.99F && Colors.luminance(0xFF000000) < 0.01F);
		assertEquals(0xFF111111, Colors.readableOn(0xFFE9D8FF, 0xFF111111, 0xFFEEEEEE));
		assertEquals(0xFFEEEEEE, Colors.readableOn(0xFF5B3FD6, 0xFF111111, 0xFFEEEEEE));
	}

	@Test
	void hsvRoundTrips() {
		for (int colour : new int[] {0xFFB79CFF, 0xFFFF7A93, 0xFF8CE0B5, 0xFF000000, 0xFFFFFFFF, 0xFF808080, 0xFF0000FF}) {
			float[] hsv = Colors.toHsv(colour);
			assertEquals(colour, Colors.fromHsv(hsv[0], hsv[1], hsv[2], 255), Integer.toHexString(colour));
		}

		assertEquals(0xFFFF0000, Colors.fromHsv(0.0F, 1.0F, 1.0F, 255));
		assertEquals(0xFFFF0000, Colors.fromHsv(1.0F, 1.0F, 1.0F, 255), "hue wraps around");
		assertEquals(0x8000FF00, Colors.fromHsv(1.0F / 3.0F, 1.0F, 1.0F, 0x80));
	}

	@Test
	void easingsStartAtZeroAndEndAtOne() {
		for (Easing easing : Easing.values()) {
			assertEquals(0.0F, easing.apply(0.0F), 1.0e-5F, easing.name());
			assertEquals(1.0F, easing.apply(1.0F), 1.0e-5F, easing.name());
		}

		assertTrue(Easing.OUT_CUBIC.apply(0.5F) > 0.5F, "ease-out is ahead of linear");
		assertEquals(0.5F, Easing.IN_OUT_CUBIC.apply(0.5F), 1.0e-5F);
		assertTrue(Easing.OUT_BACK.apply(0.7F) > 1.0F, "back overshoots before settling");
	}

	@Test
	void animationFollowsRealTime() {
		AtomicLong clock = new AtomicLong(5_000_000_000L);
		Animation animation = new Animation(0.0F, Animation.NORMAL, Easing.LINEAR, clock::get);
		assertEquals(0.0F, animation.value());
		assertFalse(animation.isRunning());

		animation.animateTo(1.0F);
		assertTrue(animation.isRunning());
		clock.addAndGet(50_000_000L);
		assertEquals(0.25F, animation.value(), 1.0e-4F);
		clock.addAndGet(100_000_000L);
		assertEquals(0.75F, animation.value(), 1.0e-4F);
		clock.addAndGet(1_000_000_000L);
		assertEquals(1.0F, animation.value());
		assertFalse(animation.isRunning());
	}

	@Test
	void retargetingMidFlightDoesNotJump() {
		AtomicLong clock = new AtomicLong();
		Animation animation = new Animation(0.0F, Animation.NORMAL, Easing.OUT_CUBIC, clock::get);
		animation.animateTo(true);
		clock.addAndGet(80_000_000L);
		float midway = animation.value();
		assertTrue(midway > 0.0F && midway < 1.0F);

		animation.animateTo(false);
		assertEquals(midway, animation.value(), 1.0e-5F, "the new run starts from where the old one was");
		clock.addAndGet(Animation.NORMAL * 1_000_000L);
		assertEquals(0.0F, animation.value());

		animation.animateTo(false);
		assertFalse(animation.isRunning(), "animating to the current target is a no-op");
		animation.snapTo(0.4F);
		assertEquals(0.4F, animation.value());
	}

	@Test
	void themesAreBuiltFromABaseAndAnAccent() {
		Theme theme = Theme.of("test", "Test", Theme.Base.DARK, 0x80B79CFF);
		assertEquals(0xFFB79CFF, theme.accent(), "the accent is always opaque");
		assertTrue(Colors.luminance(theme.accentHover()) > Colors.luminance(theme.accent()));
		assertTrue(Colors.luminance(theme.accentPressed()) < Colors.luminance(theme.accent()));
		assertTrue(Colors.luminance(theme.gradientStart()) > Colors.luminance(theme.gradientEnd()));
		assertEquals(0xFF0E0B16, theme.onAccent(), "dark text on a light accent");
		assertEquals(0xFFFFFFFF, Theme.of("deep", "Deep", Theme.Base.DARK, 0xFF3B2A8C).onAccent());
		assertTrue(theme.dark());
		assertFalse(Theme.of("day", "Day", Theme.Base.LIGHT, 0xFF7C5CFF).dark());
	}

	@Test
	void presetsAndAccentOverride() {
		ThemeManager themes = new ThemeManager();
		assertEquals("wisteria", themes.presetId());
		assertEquals(0xFFB79CFF, themes.current().accent());
		assertEquals(List.of("wisteria", "sakura", "mint", "ember", "frost"), themes.presetIds());

		int[] changes = {0};
		themes.onChange(() -> changes[0]++);
		themes.usePreset("Mint");
		assertEquals("mint", themes.presetId());
		assertNotEquals(0xFFB79CFF, themes.current().accent());
		themes.useAccent(0xFF336699);
		assertEquals(0xFF336699, themes.current().accent());
		assertEquals("mint", themes.presetId(), "an accent override keeps the preset it sits on");
		assertEquals(0xFF336699, themes.customAccent());
		themes.usePreset("wisteria");
		assertNull(themes.customAccent());
		assertEquals(3, changes[0]);
		assertThrows(IllegalArgumentException.class, () -> themes.usePreset("neon"));
	}

	@Test
	void themeChoiceSurvivesARestart(@TempDir Path gameDirectory) {
		FakePlatform platform = new FakePlatform(gameDirectory);
		Client client = new Client(platform);
		platform.typeInChat(".theme sakura");
		assertEquals("Theme: Sakura", platform.lastChatLine());
		platform.typeInChat(".theme accent #336699");
		assertEquals("Accent: #FF336699", platform.lastChatLine());
		platform.typeInChat(".theme accent pink");
		assertTrue(platform.lastChatLine().startsWith("Expected a colour"));
		assertEquals(List.of("accent", "ember", "frost", "mint", "sakura", "wisteria"), platform.tab(".theme ").candidates());
		platform.stop();

		Client after = new Client(new FakePlatform(gameDirectory));
		assertEquals("sakura", after.themes().presetId());
		assertEquals(0xFF336699, after.themes().current().accent());

		FakePlatform third = new FakePlatform(gameDirectory);
		Client reset = new Client(third);
		third.typeInChat(".theme frost");
		third.stop();
		Client last = new Client(new FakePlatform(gameDirectory));
		assertEquals("frost", last.themes().presetId());
		assertNull(last.themes().customAccent());
		assertEquals(reset.themes().current().accent(), last.themes().current().accent());
		assertNotEquals(client.themes().current().accent(), last.themes().current().accent());
	}
}
