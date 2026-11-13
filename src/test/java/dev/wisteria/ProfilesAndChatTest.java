package dev.wisteria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wisteria.io.Storage;
import dev.wisteria.module.Category;
import dev.wisteria.module.Module;
import dev.wisteria.modules.Modules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Profiles keep their data apart; prefixed chat never reaches the server. */
class ProfilesAndChatTest {
	@TempDir
	Path game;

	static final class Notifier extends Module {
		Notifier() {
			super("Notifier", "Test stand-in", Category.MISC);
		}

		Storage files() {
			return this.storage();
		}
	}

	private Client start(FakePlatform platform) {
		List<Module> modules = new ArrayList<>(Modules.create());
		modules.add(new Notifier());
		return new Client(platform, modules);
	}

	@Test
	void eachProfileHasItsOwnDataFolderAndNewOnesStartEmpty() throws Exception {
		FakePlatform platform = new FakePlatform(this.game);
		Client client = this.start(platform);
		Notifier notifier = client.modules().get(Notifier.class);
		platform.typeInChat(".esp range 48");
		notifier.files().writeText("found.txt", "main base");

		platform.typeInChat(".profile create twink");
		assertEquals("default", client.config().profile(), "creating does not switch");
		platform.typeInChat(".profiles active twink");
		assertEquals("twink", client.config().profile(), "the menu's Active choice switches");
		assertEquals("64", client.modules().get("esp").setting("range").format(), "a new profile starts with defaults");
		assertFalse(notifier.files().exists("found.txt"), "and without the other profile's data");
		notifier.files().writeText("found.txt", "twink stuff");
		assertTrue(Files.isRegularFile(this.game.resolve("wisteria/profiles/twink/data/notifier/found.txt")));

		platform.typeInChat(".profile load default");
		assertEquals("main base", notifier.files().readText("found.txt").orElseThrow());
		assertEquals("48", client.modules().get("esp").setting("range").format());
	}

	@Test
	void secretsAreKeptPerProfile() throws Exception {
		Files.createDirectories(this.game.resolve("wisteria"));
		Files.writeString(this.game.resolve("wisteria/.env"), "TELEGRAM_TOKEN=main-token-123\nTELEGRAM_TOKEN@twink=twink-token-456\n");
		FakePlatform platform = new FakePlatform(this.game);
		Client client = this.start(platform);
		assertEquals("main-token-123", client.secrets().forCore(dev.wisteria.io.Secrets.keyFor("TELEGRAM_TOKEN", "default")).orElseThrow());
		assertEquals("TELEGRAM_TOKEN@twink", dev.wisteria.io.Secrets.keyFor("TELEGRAM_TOKEN", "twink"));
		platform.typeInChat(".profile create other");
		platform.typeInChat(".profile load other");
		assertTrue(client.secrets().get("notifier", "TELEGRAM_TOKEN", "other").isEmpty(), "no falling back to another profile's token");
		assertEquals("twink-token-456", client.secrets().get("notifier", "TELEGRAM_TOKEN", "twink").orElseThrow().name().equals("TELEGRAM_TOKEN@twink")
			? client.secrets().forCore("TELEGRAM_TOKEN@twink").orElseThrow() : "");
	}

	@Test
	void prefixedChatNeverReachesTheServer() {
		FakePlatform platform = new FakePlatform(this.game);
		this.start(platform);
		assertTrue(platform.typeInChat(".nosuchcommand"));
		assertTrue(platform.chatLines.getLast().startsWith("Unknown command"));
		assertTrue(platform.typeInChat("."));
		assertTrue(platform.typeInChat(".toggle"), "a usage error stays in the client too");

		platform.typeInChat(".chat chat-commands off");
		int before = platform.chatLines.size();
		assertTrue(platform.typeInChat(".toggle esp on"), "commands off: still held back");
		assertTrue(platform.chatLines.getLast().contains("Chat commands are off"));
		assertEquals(before + 1, platform.chatLines.size());
		assertFalse(platform.typeInChat("hello"), "ordinary chat goes as usual");
	}

	@Test
	void friendsAndHostsFromTheMenuSections() {
		FakePlatform platform = new FakePlatform(this.game);
		Client client = this.start(platform);
		platform.typeInChat(".friends friends add Popbob");
		assertTrue(client.relations().isFriend("Popbob"));
		platform.typeInChat(".friends friends remove Popbob");
		assertFalse(client.relations().isFriend("Popbob"));
		platform.typeInChat(".network allowed-hosts add api.telegram.org");
		assertEquals(List.of("api.telegram.org"), client.net().allowedHosts());
		platform.typeInChat(".chat prefix ;");
		assertEquals(";", client.commands().prefix());
	}
}
