package dev.wisteria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import dev.wisteria.platform.Key;
import dev.wisteria.setting.BoolSetting;
import dev.wisteria.setting.ColorSetting;
import dev.wisteria.setting.DoubleSetting;
import dev.wisteria.setting.EnumSetting;
import dev.wisteria.setting.FloatSetting;
import dev.wisteria.setting.IntSetting;
import dev.wisteria.setting.KeySetting;
import dev.wisteria.setting.Setting;
import dev.wisteria.setting.StringSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettingsTest {
	enum Mode {
		BOX, OUTLINE, TWO_D
	}

	@Test
	void idsComeFromNames() {
		assertEquals("line-width", Setting.toId("Line Width"));
		assertEquals("through-walls", new BoolSetting("Through Walls", "", true).id());
	}

	@Test
	void numbersAreClampedAndSnapped() {
		DoubleSetting range = new DoubleSetting("Range", "", 64.0, 8.0, 256.0, 0.5);
		range.set(1000.0);
		assertEquals(256.0, range.get());
		range.set(-3.0);
		assertEquals(8.0, range.get());
		range.set(12.3);
		assertEquals(12.5, range.get());
		range.set(Double.NaN);
		assertEquals(12.5, range.get());

		IntSetting fill = new IntSetting("Fill", "", 40, 0, 255);
		fill.parse("300");
		assertEquals(255, fill.get());
		fill.setFraction(0.5);
		assertEquals(128, fill.get());

		FloatSetting width = new FloatSetting("Width", "", 2.0F, 0.5F, 6.0F, 0.5F);
		width.parse("2,7");
		assertEquals(2.5F, width.get());
		assertEquals("2.5", width.format());
	}

	@Test
	void steppedDoublesStayReadable() {
		DoubleSetting step = new DoubleSetting("Step", "", 0.0, 0.0, 1.0, 0.1);
		step.set(0.3);
		assertEquals("0.3", step.format());
		assertEquals(new JsonPrimitive(0.3), step.toJson());
	}

	@Test
	void listenersFireOnlyOnRealChanges() {
		BoolSetting flag = new BoolSetting("Flag", "", false);
		List<Boolean> seen = new ArrayList<>();
		flag.onChange(seen::add);
		assertFalse(flag.set(false));
		assertTrue(flag.set(true));
		flag.parse("toggle");
		assertEquals(List.of(true, false), seen);
		assertThrows(IllegalArgumentException.class, () -> flag.parse("maybe"));
	}

	@Test
	void jsonRoundTrips() {
		ColorSetting color = new ColorSetting("Color", "", 0xFFB79CFF);
		color.parse("#8cE0b5");
		assertEquals(0xFF8CE0B5, color.get());
		assertEquals(new JsonPrimitive("#FF8CE0B5"), color.toJson());
		color.parse("#408B6CFF");
		assertEquals(0x40, color.alpha());
		assertThrows(IllegalArgumentException.class, () -> color.parse("purple"));

		EnumSetting<Mode> mode = new EnumSetting<>("Mode", "", Mode.BOX);
		mode.parse("two-d");
		assertEquals(Mode.TWO_D, mode.get());
		assertEquals(new JsonPrimitive("two-d"), mode.toJson());
		assertEquals(List.of("box", "outline", "two-d"), mode.suggestions());
		mode.cycle();
		assertEquals(Mode.BOX, mode.get());

		KeySetting bind = new KeySetting("Bind", "");
		assertEquals(JsonNull.INSTANCE, bind.toJson());
		bind.parse("right_shift");
		assertEquals(Optional.of(Key.RIGHT_SHIFT), bind.get());
		assertEquals(new JsonPrimitive("RIGHT_SHIFT"), bind.toJson());
		assertTrue(bind.matches(Key.RIGHT_SHIFT));
		bind.parse("none");
		assertFalse(bind.matches(Key.RIGHT_SHIFT));
		assertThrows(IllegalArgumentException.class, () -> bind.parse("71"));

		StringSetting text = new StringSetting("Text", "", "Wisteria", 8);
		text.set("much too long");
		assertEquals("much too", text.get());
	}

	@Test
	void badJsonLeavesTheValueAlone() {
		IntSetting fill = new IntSetting("Fill", "", 40, 0, 255);
		fill.fromJson(new JsonPrimitive("lots"));
		assertEquals(40, fill.get());

		ColorSetting color = new ColorSetting("Color", "", 0xFFB79CFF);
		color.fromJson(new JsonPrimitive("#nothex"));
		color.fromJson(new JsonPrimitive(12));
		assertEquals(0xFFB79CFF, color.get());

		EnumSetting<Mode> mode = new EnumSetting<>("Mode", "", Mode.OUTLINE);
		mode.fromJson(new JsonPrimitive("gone-in-this-version"));
		assertEquals(Mode.OUTLINE, mode.get());

		KeySetting bind = new KeySetting("Bind", "", Key.G);
		bind.fromJson(new JsonPrimitive("KEY_THAT_DOES_NOT_EXIST"));
		assertEquals(Optional.of(Key.G), bind.get());
	}
}
