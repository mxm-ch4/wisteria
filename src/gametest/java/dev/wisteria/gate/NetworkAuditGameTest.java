package dev.wisteria.gate;

import static dev.wisteria.gate.GameTestKit.check;

import dev.wisteria.Client;
import dev.wisteria.Wisteria;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The network audit: everything a real server receives from this client, from the handshake to a minute in the world,
 * written to gate/audit-wisteria.txt. The gate's last launch runs the same client with Wisteria switched off
 * (-Dwisteria.disabled=true: its code loads but does nothing) and compares: gate/audit-diff.txt lists every
 * difference, and only the deliberate ones may remain.
 */
public final class NetworkAuditGameTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("WisteriaGate");
	/**
	 * Differences kept on purpose, all custom payloads: the brand says "vanilla" instead of "fabric", and no mod channel
	 * list or mod data is sent. Any other difference fails the audit.
	 */
	private static final List<String> ALLOWED = List.of("minecraft:custom_payload ");

	@Override
	public void runTest(ClientGameTestContext context) {
		if (GameTestKit.isRestartPhase()) {
			return;
		}

		boolean plain = GameTestKit.isAuditPhase();
		Map<String, Integer> lines;
		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			AuditLog.start();
			try (TestDedicatedServerConnection connection = server.connect()) {
				context.waitTicks(60);
				if (!plain) {
					this.checksInTheWorld(context);
				} else {
					// As long as the passive-module window of the other launch, so both see the same keep-alives.
					context.waitTicks(PASSIVE_WINDOW_TICKS);
				}

				// About 20 s connected in both launches: past the server's keep-alive at 15 s, well before the next at 30 s.
				context.waitTicks(100);

				// The same ordinary chat line in both launches, so the logs compare like for like.
				int before = AuditLog.chatPackets();
				context.runOnClient(minecraft -> minecraft.player.connection.sendChat("hello there"));
				context.waitTicks(5);
				check("audit: ordinary chat still goes out", AuditLog.chatPackets() == before + 1);
			}

			context.waitTicks(10);
			AuditLog.stop();
			lines = AuditLog.lines();
		}

		Path dir = GameTestKit.gateDirectory(context);
		try {
			Files.createDirectories(dir);
			Files.write(dir.resolve(plain ? "audit-plain.txt" : "audit-wisteria.txt"), format(lines), StandardCharsets.UTF_8);
			if (!plain) {
				check("audit: nothing the server got mentions the client", lines.keySet().stream().noneMatch(line -> line.toLowerCase(Locale.ROOT).contains("wisteria")));
				return;
			}

			Path withWisteria = dir.resolve("audit-wisteria.txt");
			check("audit: the run with Wisteria left its log", Files.isRegularFile(withWisteria));
			TreeSet<String> ours = keys(Files.readAllLines(withWisteria, StandardCharsets.UTF_8));
			TreeSet<String> theirs = new TreeSet<>(lines.keySet());
			List<String> diff = new ArrayList<>();
			List<String> unexpected = new ArrayList<>();
			for (String line : ours) {
				if (!theirs.contains(line)) {
					diff.add("+ with Wisteria only: " + line);
					if (ALLOWED.stream().noneMatch(line::contains)) {
						unexpected.add(line);
					}
				}
			}

			for (String line : theirs) {
				if (!ours.contains(line)) {
					diff.add("- without Wisteria only: " + line);
					if (ALLOWED.stream().noneMatch(line::contains)) {
						unexpected.add(line);
					}
				}
			}

			Files.write(dir.resolve("audit-diff.txt"), diff, StandardCharsets.UTF_8);
			LOG.info("audit: {} lines with Wisteria, {} without; differences: {}", ours.size(), theirs.size(), diff);
			check("audit: the only differences are the deliberate ones (" + unexpected + ")", unexpected.isEmpty());
			LOG.info("audit check passed");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	// While connected: chat that would leak is held back, client messages stay local, our keys translate to nothing.
	private void checksInTheWorld(ClientGameTestContext context) {
		Client client = Wisteria.client();
		int before = AuditLog.chatPackets();
		context.runOnClient(minecraft -> {
			client.proxies().add(dev.wisteria.proxy.ProxyConfig.parse("audit socks5 10.9.8.7:1080"));
			for (String line : List.of("my client is Wisteria", ".nosuchcommand", "proxy at 10.9.8.7:1080 if you need it", "join me via 10.9.8.7")) {
				minecraft.player.connection.sendChat(line);
			}

			minecraft.player.connection.sendCommand("msg Steve the client is wisteria");
			client.proxies().remove("audit");
		});
		context.runOnClient(minecraft -> {
			for (int i = 0; i < 5; i++) {
				client.platform().chat().print("local note " + i);
			}
		});
		context.waitTicks(10);
		check("audit: guarded lines and local notes sent nothing (" + (AuditLog.chatPackets() - before) + " chat packets)", AuditLog.chatPackets() == before);
		int guarded = context.computeOnClient(minecraft -> client.privacy().guardCount());
		check("audit: the chat guard counted them (" + guarded + ")", guarded >= 4);

		// A server probing with our names gets back the raw key, as from a vanilla client.
		String probe = context.computeOnClient(minecraft -> Component.translatable("key.wisteria.clickgui").getString());
		check("audit: our names are not translation keys (" + probe + ")", probe.equals("key.wisteria.clickgui") && I18n.get("key.wisteria.clickgui").equals("key.wisteria.clickgui"));
		boolean keys = context.computeOnClient(minecraft -> java.util.Arrays.stream(minecraft.options.keyMappings)
			.anyMatch(mapping -> mapping.getName().toLowerCase(Locale.ROOT).contains("wisteria") || mapping.getCategory().toString().toLowerCase(Locale.ROOT).contains("wisteria")));
		check("audit: no key mappings of ours in the game's controls", !keys);
		long requests = context.computeOnClient(minecraft -> client.net().requestsMade());
		check("audit: the client made no request of its own (" + requests + ")", requests == 0L);
		this.passiveModules(context, client);
	}

	/** SPEC_PASSIVE: the modules that only look at what the server already sent. */
	/** Two 100-tick windows and the 20 ticks between them. */
	private static final int PASSIVE_WINDOW_TICKS = 340;
	private static final List<String> PASSIVE = List.of("xraypredicted", "media", "basefinder", "waypoints", "newchunks", "storageesp", "shulkerpreview", "durability", "highway");

	/**
	 * The same idle player for five seconds with the passive modules off, then five seconds with them on (Xray with a
	 * seed, so it really predicts): the server must get the same packets, type for type.
	 */
	private void passiveModules(ClientGameTestContext context, Client client) {
		dev.wisteria.modules.client.SeedsSection seeds = client.modules().get(dev.wisteria.modules.client.SeedsSection.class);
		dev.wisteria.setting.PasswordSetting seed = (dev.wisteria.setting.PasswordSetting) seeds.setting("seed");
		context.runOnClient(minecraft -> seed.store("20261114"));
		Map<String, Integer> off = this.window(context);
		long requestsBefore = context.computeOnClient(minecraft -> client.net().requestsMade());
		context.runOnClient(minecraft -> PASSIVE.forEach(id -> client.modules().get(id).setEnabled(true)));
		// SeedcrackerX handing over a seed, as it would after cracking one: it only goes into the profile.
		// From its own thread, as its cracker does.
		Thread cracker = new Thread(() -> net.fabricmc.loader.api.FabricLoader.getInstance()
			.getEntrypoints("seedcrackerx", kaptainwutax.seedcrackerX.api.SeedCrackerAPI.class)
			.forEach(entrypoint -> entrypoint.pushWorldSeed(20261115L)), "fake SeedcrackerX");
		cracker.start();
		try {
			cracker.join(5_000L);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}

		context.waitTicks(20);
		Map<String, Integer> on = this.window(context);
		boolean working = context.computeOnClient(minecraft -> ((dev.wisteria.modules.xray.XrayPredicted) client.modules().get("xraypredicted")).ready());
		context.runOnClient(minecraft -> {
			PASSIVE.forEach(id -> client.modules().get(id).setEnabled(false));
			seed.store("");
		});
		long requests = context.computeOnClient(minecraft -> client.net().requestsMade()) - requestsBefore;

		List<String> differences = new ArrayList<>();
		TreeSet<String> types = new TreeSet<>(off.keySet());
		types.addAll(on.keySet());
		for (String type : types) {
			int without = off.getOrDefault(type, 0);
			int with = on.getOrDefault(type, 0);
			// Same kinds both times; counts may differ by the odd tick at either end of the window.
			if (without == 0 != (with == 0) || Math.abs(with - without) > 2) {
				differences.add(type + " off ×" + without + ", on ×" + with);
			}
		}

		LOG.info("audit: passive modules {}: off {}, on {}", PASSIVE, off, on);
		check("audit: Xray Predicted was really predicting during the window", working);
		check("audit: passive modules on send nothing extra (" + differences + ")", differences.isEmpty());
		check("audit: passive modules made no requests (" + requests + ")", requests == 0L);

		// The panic button: modules on, then panic; the next 100 ticks must look like the idle window.
		context.runOnClient(minecraft -> {
			PASSIVE.forEach(id -> client.modules().get(id).setEnabled(true));
			client.panic().trigger(false, false);
		});
		context.waitTicks(20);
		Map<String, Integer> panicked = this.window(context);
		boolean wasOn = context.computeOnClient(minecraft -> client.panic().active());
		context.runOnClient(minecraft -> {
			client.panic().restore();
			PASSIVE.forEach(id -> client.modules().get(id).setEnabled(false));
		});
		List<String> panicDifferences = new ArrayList<>();
		TreeSet<String> panicTypes = new TreeSet<>(off.keySet());
		panicTypes.addAll(panicked.keySet());
		for (String type : panicTypes) {
			int without = off.getOrDefault(type, 0);
			int with = panicked.getOrDefault(type, 0);
			if (without == 0 != (with == 0) || Math.abs(with - without) > 2) {
				panicDifferences.add(type + " idle ×" + without + ", panic ×" + with);
			}
		}

		LOG.info("audit: panic window {}", panicked);
		check("audit: the panic was on during its window", wasOn);
		check("audit: the panic sends nothing (" + panicDifferences + ")", panicDifferences.isEmpty());
	}

	/**
	 * Replies the client owes the server, not things it decides to send: the server pings every 15 s and the client
	 * answers; the server sends chunks in batches and the client acknowledges each. Whether one lands in a 5 s window
	 * depends only on the server. Left out of window comparisons
	 * (the whole-session audit above still compares them).
	 */
	private static final java.util.Set<String> SERVER_DRIVEN = java.util.Set.of("minecraft:keep_alive", "minecraft:pong",
		"minecraft:chunk_batch_received");

	/** What the server got during 100 ticks of standing still, without the replies to the server's own pings. */
	private Map<String, Integer> window(ClientGameTestContext context) {
		Map<String, Integer> before = AuditLog.lines();
		context.waitTicks(100);
		Map<String, Integer> after = AuditLog.lines();
		Map<String, Integer> delta = new java.util.TreeMap<>();
		after.forEach((line, count) -> {
			int added = count - before.getOrDefault(line, 0);
			if (added > 0 && !SERVER_DRIVEN.contains(line)) {
				delta.put(line, added);
			}
		});
		return delta;
	}

	private static List<String> format(Map<String, Integer> lines) {
		List<String> out = new ArrayList<>();
		new TreeMap<>(lines).forEach((line, count) -> out.add(line + "\t×" + count));
		return out;
	}

	private static TreeSet<String> keys(List<String> file) {
		TreeSet<String> keys = new TreeSet<>();
		for (String line : file) {
			int tab = line.lastIndexOf('\t');
			keys.add(tab < 0 ? line : line.substring(0, tab));
		}

		return keys;
	}
}
