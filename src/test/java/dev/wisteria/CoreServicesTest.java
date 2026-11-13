package dev.wisteria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wisteria.io.Net;
import dev.wisteria.io.Secret;
import dev.wisteria.io.Storage;
import dev.wisteria.module.Category;
import dev.wisteria.module.Module;
import dev.wisteria.modules.Modules;
import dev.wisteria.modules.fullbright.FullBright;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Storage, secrets and network as modules see them, and the .net command. */
class CoreServicesTest {
	private static final String TOKEN = "123456:AAH-made-up-token-for-tests";

	@TempDir
	Path game;

	/** Named like the one module the core lets use the network and secrets. */
	static final class Notifier extends Module {
		Notifier() {
			super("Notifier", "Test stand-in", Category.MISC);
		}

		Storage files() {
			return this.storage();
		}

		Optional<Secret> token() {
			return this.secret("TELEGRAM_BOT_TOKEN");
		}

		void say(String text) {
			this.info(text);
		}
	}

	static final class Nuker extends Module {
		Nuker() {
			super("Nuker", "Test stand-in", Category.WORLD);
		}

		void tryNetwork() {
			this.send(Net.Request.get("https://api.telegram.org/"));
		}

		void trySecret() {
			this.secret("TELEGRAM_BOT_TOKEN");
		}
	}

	private Client start(FakePlatform platform) {
		List<Module> modules = new ArrayList<>(Modules.create());
		modules.add(new Notifier());
		modules.add(new Nuker());
		return new Client(platform, modules);
	}

	@Test
	void allowedHostsAreTheUsersAndSurviveARestart() {
		FakePlatform platform = new FakePlatform(this.game);
		Client client = this.start(platform);
		platform.typeInChat(".net");
		assertTrue(platform.chatLines.getLast().contains("No hosts allowed"), platform.chatLines.getLast());
		platform.typeInChat(".net allow api.telegram.org");
		platform.typeInChat(".net allow not a host");
		assertTrue(platform.chatLines.getLast().contains("Usage"), platform.chatLines.getLast());
		platform.typeInChat(".net allow https://x/");
		assertTrue(platform.chatLines.getLast().contains("not a host name"), platform.chatLines.getLast());
		assertEquals(List.of("api.telegram.org"), client.net().allowedHosts());
		assertEquals(List.of("api.telegram.org"), platform.tab(".net deny ").candidates());
		platform.stop();

		FakePlatform again = new FakePlatform(this.game);
		Client after = this.start(again);
		assertEquals(List.of("api.telegram.org"), after.net().allowedHosts());
		again.typeInChat(".net deny api.telegram.org");
		assertEquals(List.of(), after.net().allowedHosts());
	}

	@Test
	void onlyPermittedModulesReachSecretsAndNetwork() throws Exception {
		Files.createDirectories(this.game.resolve("wisteria"));
		Files.writeString(this.game.resolve("wisteria/.env"), "TELEGRAM_BOT_TOKEN=" + TOKEN + "\n");
		FakePlatform platform = new FakePlatform(this.game);
		Client client = this.start(platform);
		Notifier notifier = client.modules().get(Notifier.class);
		Nuker nuker = client.modules().get(Nuker.class);

		assertEquals("TELEGRAM_BOT_TOKEN=****", notifier.token().orElseThrow().toString());
		assertThrows(SecurityException.class, nuker::trySecret);
		assertThrows(SecurityException.class, nuker::tryNetwork);

		notifier.say("token is " + TOKEN);
		assertEquals("Notifier: token is ****", platform.chatLines.getLast(), "a secret never reaches the chat");
	}

	@Test
	void eachModuleWritesOnlyToItsOwnFolder() {
		Client client = this.start(new FakePlatform(this.game));
		Storage files = client.modules().get(Notifier.class).files();
		files.writeText("sent.log", "1");
		assertTrue(Files.isRegularFile(this.game.resolve("wisteria/profiles/default/data/notifier/sent.log")), "in the active profile's folder");
	}

	@Test
	void mixinsFindModulesByClass() {
		Client client = this.start(new FakePlatform(this.game));
		FullBright fullBright = client.modules().get(FullBright.class);
		assertSame(client.modules().get("fullbright"), fullBright);
		assertFalse(client.modules().isEnabled(FullBright.class));
		client.setEnabled(fullBright, true);
		assertTrue(client.modules().isEnabled(FullBright.class));
		assertNull(new Client(new FakePlatform(this.game.resolve("other")), List.of()).modules().get(FullBright.class));
	}
}
