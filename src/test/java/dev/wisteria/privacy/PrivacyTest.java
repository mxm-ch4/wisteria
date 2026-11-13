package dev.wisteria.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wisteria.Client;
import dev.wisteria.FakePlatform;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrivacyTest {
	@TempDir
	Path game;

	@Test
	void theGuardFindsSecretsProxiesAndWords() {
		List<String> secrets = List.of("123456:AAH-token", "short");
		List<String> proxy = List.of("10.9.8.7", "10.9.8.7:1080", "alice-login");
		List<String> words = Privacy.DEFAULT_WORDS;
		assertNotNull(ChatGuard.check("here is 123456:AAH-token", secrets, proxy, words));
		assertNull(ChatGuard.check("a short story", secrets, proxy, words), "secrets under six characters are not matched");
		assertNotNull(ChatGuard.check("connect via 10.9.8.7 please", secrets, proxy, words));
		assertNotNull(ChatGuard.check("ALICE-LOGIN is my name", secrets, proxy, words));
		assertNotNull(ChatGuard.check("I use Wisteria!", secrets, proxy, words));
		assertNotNull(ChatGuard.check("open the clickgui", secrets, proxy, words));
		assertNull(ChatGuard.check("wisterias bloom in spring", secrets, proxy, words), "whole words only");
		assertNull(ChatGuard.check("hello everyone", secrets, proxy, words));
	}

	@Test
	void theGuardSkipsCommandsCountsAndCanBeSwitchedOff() throws Exception {
		Files.createDirectories(this.game.resolve("wisteria"));
		Files.writeString(this.game.resolve("wisteria/.env"), "TELEGRAM_TOKEN=999999:secret-value\n");
		FakePlatform platform = new FakePlatform(this.game);
		Client client = new Client(platform);
		assertNull(client.privacy().guard(".toggle esp"), "prefixed lines are the command manager's, never sent anyway");
		assertNotNull(client.privacy().guard("token 999999:secret-value"));
		assertNotNull(client.privacy().guard("/msg Steve wisteria"));
		assertEquals(2, client.privacy().guardCount());
		platform.typeInChat(".privacy chat-guard off");
		assertNull(client.privacy().guard("I use wisteria"));
	}

	@Test
	void brandAndWordsAreSettingsThatSurviveARestart() {
		FakePlatform platform = new FakePlatform(this.game);
		Client client = new Client(platform);
		assertEquals("vanilla", client.privacy().brand(), "the game's own brand by default");
		assertTrue(client.privacy().hideChannels());
		platform.typeInChat(".privacy guard-words add mybase");
		platform.typeInChat(".privacy guard-words remove chams");
		platform.typeInChat(".privacy client-brand lunar");
		platform.stop();
		Client after = new Client(new FakePlatform(this.game));
		assertTrue(after.privacy().words().contains("mybase"));
		assertFalse(after.privacy().words().contains("chams"));
		assertEquals("lunar", after.privacy().brand());
		assertNotNull(after.privacy().guard("meet at mybase"));
	}

	@Test
	void nothingLeavesUntilTheUserAllowsIt() {
		Client client = new Client(new FakePlatform(this.game));
		assertEquals(0L, client.net().requestsMade());
		assertTrue(client.net().allowedHosts().isEmpty(), "no host is allowed out of the box");
	}

	// Server-probeable resources: nothing of ours replaces the game's assets, and we add no translation keys.
	@Test
	void ourAssetsStayInOurNamespaceAndHaveNoTranslations() throws Exception {
		Path assets = Path.of("src/main/resources/assets");
		try (Stream<Path> namespaces = Files.list(assets)) {
			assertEquals(List.of("wisteria"), namespaces.map(path -> path.getFileName().toString()).toList());
		}

		try (Stream<Path> files = Files.walk(assets)) {
			assertTrue(files.noneMatch(path -> path.toString().contains("/lang/")), "no language files: our names are not translation keys");
		}

		assertFalse(Files.exists(Path.of("src/main/resources/data")), "no data pack");
	}
}
