package dev.wisteria.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecretsTest {
	private static final String TOKEN = "123456:AAH-made-up-token-for-tests";

	@TempDir
	Path root;
	private Secrets secrets;

	@BeforeEach
	void write() throws Exception {
		Files.writeString(this.root.resolve(".env"), """
			# Telegram
			TELEGRAM_BOT_TOKEN=%s
			export DISCORD_WEBHOOK="https://discord.com/api/webhooks/1/made-up"
			EMPTY=
			not a line
			""".formatted(TOKEN));
		this.secrets = new Secrets(this.root);
	}

	@Test
	void readsTheEnvFileForModulesAllowedToHaveSecrets() {
		Secret token = this.secrets.get("notifier", "TELEGRAM_BOT_TOKEN").orElseThrow();
		assertEquals(TOKEN, token.value());
		assertEquals("https://discord.com/api/webhooks/1/made-up", this.secrets.get("notifier", "DISCORD_WEBHOOK").orElseThrow().value());
		assertEquals(Optional.empty(), this.secrets.get("notifier", "EMPTY"));
		assertEquals(Optional.empty(), this.secrets.get("notifier", "MISSING"));
		assertEquals(List.of("TELEGRAM_BOT_TOKEN", "DISCORD_WEBHOOK", "EMPTY"), this.secrets.names());
	}

	@Test
	void otherModulesGetNothing() {
		assertThrows(SecurityException.class, () -> this.secrets.get("nuker", "TELEGRAM_BOT_TOKEN"));
	}

	@Test
	void valuesNeverPrint() {
		Secret token = this.secrets.get("notifier", "TELEGRAM_BOT_TOKEN").orElseThrow();
		assertEquals("TELEGRAM_BOT_TOKEN=****", token.toString());
		assertEquals("sending to bot**** failed", this.secrets.redact("sending to bot" + TOKEN + " failed"));

		Throwable redacted = this.secrets.redact(new IllegalStateException("bad url bot" + TOKEN, new RuntimeException(TOKEN)));
		assertFalse(redacted.toString().contains(TOKEN));
		assertFalse(redacted.getCause().toString().contains(TOKEN));
		assertTrue(redacted.getMessage().startsWith("java.lang.IllegalStateException: bad url bot****"));
	}

	@Test
	void picksUpEditsOnReload() throws Exception {
		assertEquals(TOKEN, this.secrets.get("notifier", "TELEGRAM_BOT_TOKEN").orElseThrow().value());
		Files.writeString(this.root.resolve(".env"), "TELEGRAM_BOT_TOKEN=changed-value\n");
		assertEquals(TOKEN, this.secrets.get("notifier", "TELEGRAM_BOT_TOKEN").orElseThrow().value(), "read once, kept until reload");
		this.secrets.reload();
		assertEquals("changed-value", this.secrets.get("notifier", "TELEGRAM_BOT_TOKEN").orElseThrow().value());
	}
}
