package dev.wisteria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wisteria.platform.ChatBackend;
import dev.wisteria.platform.Key;
import dev.wisteria.platform.WorldBackend;
import dev.wisteria.event.Events;
import dev.wisteria.module.Category;
import dev.wisteria.module.Module;
import dev.wisteria.social.Relations;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClientTest {
	@TempDir
	Path gameDirectory;

	private FakePlatform platform;
	private Client client;

	@BeforeEach
	void start() {
		this.platform = new FakePlatform(this.gameDirectory);
		this.client = new Client(this.platform);
	}

	/** What a restart looks like to the core: a new client over the same directory. */
	private Client restart() {
		this.platform.stop();
		return this.startAgain();
	}

	/** A new client over the same directory, for when the old one has already shut down. */
	private Client startAgain() {
		this.platform = new FakePlatform(this.gameDirectory);
		this.client = new Client(this.platform);
		return this.client;
	}

	private Module module(String name) {
		Module module = this.client.modules().get(name);
		assertNotNull(module, name);
		return module;
	}

	private Path config(String relative) {
		return this.gameDirectory.resolve("wisteria").resolve(relative);
	}

	// ---- modules ----

	@Test
	void theFirstModulesAreRegistered() {
		assertEquals(List.of("fullbright", "esp", "xraypredicted", "basefinder", "clickgui", "watermark", "arraylist", "coordinates", "direction", "fps", "tps",
			"target", "potions", "notifications", "keybinds", "media", "proxystatus", "hudeditor", "proxy", "profiles", "friends", "network", "chat", "privacy",
			"seeds", "interface", "tools"), this.client.modules().all().stream().map(Module::id).toList());
		assertEquals(3, this.client.modules().in(Category.RENDER).size());
		assertEquals(13, this.client.modules().in(Category.HUD).size());
		assertTrue(this.client.modules().enabled().isEmpty());
		assertNotNull(this.client.modules().get("FullBright"));
		assertNull(this.client.modules().get("killaura"));
	}

	@Test
	void aBoundKeyTogglesTheModuleButNotWhileTyping() {
		Module esp = this.module("esp");
		esp.bind().set(Optional.of(Key.G));

		this.platform.press(Key.G, true);
		assertFalse(esp.isEnabled(), "a key typed into chat must not toggle anything");

		this.platform.press(Key.G, false);
		assertTrue(esp.isEnabled());
		this.platform.press(Key.H, false);
		assertTrue(esp.isEnabled());
		this.platform.press(Key.G, false);
		assertFalse(esp.isEnabled());
	}

	@Test
	void handlersOnlyRunWhileEnabled() {
		AtomicInteger ticks = new AtomicInteger();
		Module counter = new Module("Counter", "", Category.MISC) {
			{
				this.on(Events.Tick.class, tick -> ticks.incrementAndGet());
			}
		};
		this.client.modules().register(counter);

		this.platform.tick(3);
		assertEquals(0, ticks.get());
		counter.setEnabled(true);
		this.platform.tick(3);
		assertEquals(3, ticks.get());
		counter.setEnabled(false);
		this.platform.tick(3);
		assertEquals(3, ticks.get());
	}

	@Test
	void aCrashingHandlerSwitchesItsModuleOffAndSaysSo() {
		Module broken = new Module("Broken", "", Category.MISC) {
			{
				this.on(Events.Tick.class, tick -> {
					throw new IllegalStateException("boom");
				});
			}
		};
		this.client.modules().register(broken);
		broken.setEnabled(true);

		this.platform.tick(1);
		assertFalse(broken.isEnabled());
		assertTrue(this.platform.lastChatLine().contains("Broken crashed"), this.platform.lastChatLine());
		this.platform.tick(1);
	}

	@Test
	void aModuleThatCannotStartStaysOff() {
		Module stubborn = new Module("Stubborn", "", Category.MISC) {
			@Override
			protected void onEnable() {
				throw new IllegalStateException("not today");
			}
		};
		this.client.modules().register(stubborn);

		assertFalse(this.client.setEnabled(stubborn, true));
		assertFalse(stubborn.isEnabled());
		assertTrue(this.platform.lastChatLine().contains("could not be switched on"));
	}

	@Test
	void fullBrightDrivesTheBrightnessOverride() {
		Module fullBright = this.module("fullbright");
		fullBright.setEnabled(true);
		assertEquals(16.0F, this.platform.brightness);
		this.client.commands().execute("fullbright brightness 4", this.platform.chatLines::add);
		assertEquals(4.0F, this.platform.brightness);
		fullBright.setEnabled(false);
		assertEquals(0.0F, this.platform.brightness);
	}

	@Test
	void packetEventsCanBeCancelled() {
		this.client.bus().subscribe(Events.PacketSend.class, event -> {
			if (event.name().equals("ServerboundSwingPacket")) {
				event.cancel();
			}
		});
		assertTrue(this.platform.send("ServerboundSwingPacket"));
		assertFalse(this.platform.send("ServerboundChatPacket"));
	}

	// ---- commands ----

	@Test
	void onlyPrefixedChatIsSwallowed() {
		assertFalse(this.platform.typeInChat("hello there"));
		assertTrue(this.platform.typeInChat("."), "even the bare prefix stays in the client");
		assertTrue(this.platform.chatLines.isEmpty());

		assertTrue(this.platform.typeInChat(".toggle esp"));
		assertTrue(this.module("esp").isEnabled());
		assertEquals("ESP on", this.platform.lastChatLine());

		assertTrue(this.platform.typeInChat(".nonsense"));
		assertTrue(this.platform.lastChatLine().startsWith("Unknown command"));
	}

	@Test
	void commandsToggleBindAndConfigure() {
		this.platform.typeInChat(".t watermark on");
		this.platform.typeInChat(".t watermark on");
		assertTrue(this.module("watermark").isEnabled());

		this.platform.typeInChat(".bind esp f6");
		assertTrue(this.module("esp").bind().matches(Key.F6));
		this.platform.typeInChat(".bind esp none");
		assertEquals(Optional.empty(), this.module("esp").bind().get());

		this.platform.typeInChat(".set esp range 48");
		assertEquals("48", this.module("esp").setting("range").format());
		this.platform.typeInChat(".esp through-walls off");
		assertEquals("off", this.module("esp").setting("Through Walls").format());
		this.platform.typeInChat(".watermark text Hello anarchy");
		assertEquals("Hello anarchy", this.module("watermark").setting("text").format());

		this.platform.typeInChat(".esp");
		assertTrue(this.module("esp").isEnabled());
		this.platform.typeInChat(".reset esp range");
		assertEquals("64", this.module("esp").setting("range").format());
	}

	@Test
	void mistakesAreExplainedNotThrown() {
		this.platform.typeInChat(".toggle killaura");
		assertEquals("No module called \"killaura\"", this.platform.lastChatLine());
		this.platform.typeInChat(".set esp reach 6");
		assertTrue(this.platform.lastChatLine().startsWith("ESP has no setting \"reach\""), this.platform.lastChatLine());
		this.platform.typeInChat(".set esp range far");
		assertEquals("Expected a number, got \"far\"", this.platform.lastChatLine());
		this.platform.typeInChat(".bind esp 71");
		assertTrue(this.platform.lastChatLine().startsWith("Unknown key \"71\""));
		this.platform.typeInChat(".prefix /");
		assertTrue(this.platform.lastChatLine().startsWith("A prefix is"));
		assertEquals(".", this.client.commands().prefix());
	}

	@Test
	void friendsAndEnemiesExcludeEachOther() {
		this.platform.typeInChat(".friend add Popbob");
		assertTrue(this.client.relations().isFriend("popbob"));
		this.platform.typeInChat(".enemy add POPBOB");
		assertEquals(Relations.Relation.ENEMY, this.client.relations().of("Popbob"));
		assertTrue(this.client.relations().friends().isEmpty());
		this.platform.typeInChat(".enemy remove popbob");
		assertEquals(Relations.Relation.NEUTRAL, this.client.relations().of("Popbob"));
		this.platform.typeInChat(".friend add not a name!");
		assertTrue(this.client.relations().friends().isEmpty());
	}

	@Test
	void tabCompletion() {
		assertNull(this.platform.tab("hello"), "ordinary chat is left to the game");

		ChatBackend.Completion commands = this.platform.tab(".t");
		assertEquals(1, commands.start());
		assertEquals(List.of("target", "theme", "toggle", "tools", "tps"), commands.candidates(), "commands and module names");
		assertEquals(List.of("toggle"), this.platform.tab(".tog").candidates());
		assertTrue(this.platform.tab(".").candidates().containsAll(List.of("help", "esp", "fullbright", "profile")));

		ChatBackend.Completion modules = this.platform.tab(".toggle ");
		assertEquals(8, modules.start());
		assertEquals(List.of("arraylist", "basefinder", "clickgui", "coordinates", "direction", "esp", "fps", "fullbright", "hudeditor", "keybinds",
			"media", "notifications", "potions", "proxy", "proxystatus", "target", "tps", "watermark", "xraypredicted"), modules.candidates(), "only what switches on");
		assertEquals(List.of("fullbright"), this.platform.tab(".toggle fu").candidates());
		assertEquals(List.of("off", "on"), this.platform.tab(".toggle esp ").candidates());

		assertEquals(List.of("passive", "passive-color", "player-color", "players"), this.platform.tab(".set esp p").candidates());
		assertEquals(List.of("off", "on", "toggle"), this.platform.tab(".esp players ").candidates());
		assertEquals(List.of("bottom-left", "bottom-right"), this.platform.tab(".hud watermark bottom-").candidates());
		assertEquals(List.of("f1", "f10", "f11", "f12"), this.platform.tab(".bind esp f1").candidates());
		assertEquals(List.of("default"), this.platform.tab(".profile load ").candidates());

		this.platform.entities.add(new WorldBackend.Entity(7, WorldBackend.EntityKind.PLAYER, "Hausemaster", 0, 64, 0, 0.6F, 1.8F, false));
		this.platform.entities.add(new WorldBackend.Entity(1, WorldBackend.EntityKind.PLAYER, "Me", 0, 64, 0, 0.6F, 1.8F, true));
		assertEquals(List.of("Hausemaster"), this.platform.tab(".friend add ").candidates());
	}

	// ---- config ----

	@Test
	void everythingSurvivesARestart() {
		this.platform.typeInChat(".toggle esp on");
		this.platform.typeInChat(".bind esp g");
		this.platform.typeInChat(".esp range 48");
		this.platform.typeInChat(".esp player-color #FF8CE0B5");
		this.platform.typeInChat(".hud watermark bottom-right");
		this.platform.typeInChat(".watermark show-ping off");
		this.platform.typeInChat(".toggle fullbright on");
		this.platform.typeInChat(".friend add Popbob");
		this.platform.typeInChat(".enemy add Fit");
		this.platform.typeInChat(".prefix ;");

		Client after = this.restart();
		assertEquals(";", after.commands().prefix());
		assertTrue(this.module("esp").isEnabled());
		assertTrue(this.module("esp").bind().matches(Key.G));
		assertEquals("48", this.module("esp").setting("range").format());
		assertEquals("#FF8CE0B5", this.module("esp").setting("player-color").format());
		assertEquals(dev.wisteria.hud.Anchor.BOTTOM_RIGHT, after.hud().placement(after.hud().element("watermark")).anchor());
		assertEquals("off", this.module("watermark").setting("show-ping").format());
		assertFalse(this.module("watermark").isEnabled());
		assertTrue(after.relations().isFriend("Popbob"));
		assertTrue(after.relations().isEnemy("Fit"));
		assertEquals(16.0F, this.platform.brightness, "an enabled module is switched on again at startup, with its effect");
	}

	@Test
	void changesAreWrittenAfterAShortDelayWithoutAShutdown() throws IOException {
		this.platform.tick(60);
		this.platform.typeInChat(".esp range 100");
		this.platform.tick(10);
		assertFalse(Files.readString(this.config("profiles/default.json")).contains("\"range\": 100.0"));
		this.platform.tick(40);
		assertTrue(Files.readString(this.config("profiles/default.json")).contains("\"range\": 100.0"));
	}

	@Test
	void profilesKeepSeparateState() {
		this.platform.typeInChat(".toggle esp on");
		this.platform.typeInChat(".esp range 32");
		this.platform.typeInChat(".profile save mining");
		assertEquals("mining", this.client.config().profile());
		this.platform.typeInChat(".esp range 200");
		this.platform.typeInChat(".toggle watermark on");

		this.platform.typeInChat(".profile load default");
		assertEquals("32", this.module("esp").setting("range").format());
		assertFalse(this.module("watermark").isEnabled());

		this.platform.typeInChat(".profile load mining");
		assertEquals("200", this.module("esp").setting("range").format());
		assertTrue(this.module("watermark").isEnabled());
		assertEquals(List.of("default", "mining"), this.client.config().profiles());

		this.restart();
		assertEquals("mining", this.client.config().profile());
		assertEquals("200", this.module("esp").setting("range").format());

		this.platform.typeInChat(".profile delete mining");
		assertTrue(this.platform.lastChatLine().contains("active profile"));
		this.platform.typeInChat(".profile load nowhere");
		assertEquals("No profile called \"nowhere\"", this.platform.lastChatLine());
		this.platform.typeInChat(".profile load default");
		this.platform.typeInChat(".profile delete mining");
		assertEquals(List.of("default"), this.client.config().profiles());
		assertThrows(IllegalArgumentException.class, () -> this.client.config().saveAs("../escape"));
	}

	@Test
	void aBrokenFileIsSetAsideAndDefaultsUsed() throws IOException {
		this.platform.typeInChat(".esp range 48");
		this.platform.stop();
		Files.writeString(this.config("profiles/default.json"), "{ this is not json");

		this.startAgain();
		assertEquals("64", this.module("esp").setting("range").format());
		assertTrue(Files.exists(this.config("profiles/default.json.broken")));
	}

	@Test
	void handEditedFilesAreReadTolerantly() throws IOException {
		this.platform.stop();
		Files.writeString(this.config("profiles/default.json"), """
			{
			  "modules": {
			    "esp": { "enabled": "yes", "bind": "NO_SUCH_KEY", "settings": { "range": 99999, "fill": "opaque", "removed-setting": 1 } },
			    "removedmodule": { "enabled": true },
			    "watermark": { "enabled": true, "bind": "F7", "settings": { "scale": 2.123 } }
			  }
			}
			""");

		this.startAgain();
		Module esp = this.module("esp");
		assertFalse(esp.isEnabled());
		assertEquals(Optional.empty(), esp.bind().get());
		assertEquals("256", esp.setting("range").format());
		assertEquals("40", esp.setting("box-fill").format());
		assertTrue(this.module("watermark").isEnabled());
		assertTrue(this.module("watermark").bind().matches(Key.F7));
		assertEquals("2.12", this.module("watermark").setting("scale").format(), "snapped to the 0.01 step");
	}
}
