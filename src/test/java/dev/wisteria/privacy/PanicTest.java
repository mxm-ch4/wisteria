package dev.wisteria.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wisteria.Client;
import dev.wisteria.FakePlatform;
import dev.wisteria.FakeRenderer;
import dev.wisteria.hud.Toasts;
import dev.wisteria.platform.Key;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The panic button and Clear My Data, without the game. */
class PanicTest {
	@TempDir
	Path gameDirectory;

	private FakePlatform platform;
	private Client client;

	@BeforeEach
	void start() {
		this.platform = new FakePlatform(this.gameDirectory);
		this.client = new Client(this.platform);
	}

	private boolean on(String id) {
		return this.client.modules().get(id).isEnabled();
	}

	@Test
	void panicSwitchesEverythingOffAndRestoreBringsItBack() {
		for (String id : List.of("fullbright", "esp", "watermark", "coordinates")) {
			this.client.setEnabled(this.client.modules().get(id), true);
		}

		this.client.panic().trigger(false, false);
		assertTrue(this.client.panic().active());
		for (String id : List.of("fullbright", "esp", "watermark", "coordinates")) {
			assertFalse(this.on(id), id + " off");
		}

		assertEquals(List.of("fullbright", "esp", "watermark", "coordinates"), this.client.panic().remembered().stream()
			.filter(id -> List.of("fullbright", "esp", "watermark", "coordinates").contains(id)).toList());
		this.client.panic().restore();
		assertFalse(this.client.panic().active());
		for (String id : List.of("fullbright", "esp", "watermark", "coordinates")) {
			assertTrue(this.on(id), id + " back on");
		}
	}

	@Test
	void heldKeysAreLetGo() {
		int before = this.platform.releasedAll;
		this.client.panic().trigger(false, false);
		assertEquals(before + 1, this.platform.releasedAll);
	}

	@Test
	void nothingIsPrintedToastedOrDrawnWhileItIsOn() {
		this.client.setEnabled(this.client.modules().get("coordinates"), true);
		this.client.panic().trigger(false, false);
		int lines = this.platform.chatLines.size();
		this.client.platform().chat().print("a local line");
		this.platform.typeInChat(".toggle fullbright");
		assertEquals(lines, this.platform.chatLines.size(), "no local chat output");
		this.client.hud().notify("t", "x", Toasts.Kind.INFO);
		assertTrue(this.client.hud().toasts().shown().isEmpty(), "no toasts");
		assertTrue(this.client.hud().render(new FakeRenderer(480.0F, 270.0F), false).isEmpty(), "no HUD");

		this.client.panic().restore();
		this.client.platform().chat().print("back");
		assertEquals("back", this.platform.lastChatLine());
	}

	@Test
	void bindsWaitExceptTheMenuAndThePanicKeyRestores() {
		this.platform.typeInChat(".privacy panic-key f9");
		this.platform.typeInChat(".bind fullbright g");
		this.platform.press(Key.F9, false);
		assertTrue(this.client.panic().active(), "the panic key panics");
		this.platform.press(Key.G, false);
		assertFalse(this.on("fullbright"), "other binds wait during the panic");
		this.platform.press(Key.F9, false);
		assertFalse(this.client.panic().active(), "the panic key again restores");
		this.platform.press(Key.G, false);
		assertTrue(this.on("fullbright"), "binds work again");
	}

	@Test
	void clearMyDataDeletesOnlyInsideTheDataFolder() throws Exception {
		Path root = this.gameDirectory.resolve("wisteria");
		Files.createDirectories(root.resolve("profiles/default/data/seeds"));
		Files.writeString(root.resolve(".env"), "TOKEN=abcdefgh123\n");
		Files.writeString(root.resolve("profiles/default/data/seeds/seeds.json"), "{\"a\":1}");
		Path outside = this.gameDirectory.resolve("options.txt");
		Files.writeString(outside, "the game's own file");
		this.client.relations().set("Somebody", dev.wisteria.social.Relations.Relation.FRIEND);
		this.client.setEnabled(this.client.modules().get("esp"), true);

		int deleted = this.client.clearMyData();
		assertTrue(deleted >= 2, "deleted " + deleted);
		assertTrue(Files.isDirectory(root), "the folder stays");
		assertFalse(Files.exists(root.resolve(".env")));
		assertFalse(Files.exists(root.resolve("profiles/default/data/seeds/seeds.json")));
		assertTrue(Files.exists(outside), "nothing outside the folder is touched");
		assertTrue(this.client.secrets().values().isEmpty(), "no secrets left in memory");
		assertTrue(this.client.relations().friends().isEmpty(), "no friends left");
		assertFalse(this.on("esp"));
	}

	@Test
	void clearMyDataNeedsTheConfirmation() {
		var setting = (dev.wisteria.setting.InputSetting) this.client.modules().get("privacy").setting("clear-my-data");
		assertFalse(setting.submit("yes"), "anything but DELETE is refused");
		assertTrue(setting.lastError() != null && !setting.lastError().isEmpty());
		assertThrows(IllegalArgumentException.class, () -> DataWipe.wipe(Path.of("/")));
	}
}
