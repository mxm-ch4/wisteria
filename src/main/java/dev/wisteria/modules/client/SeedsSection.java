package dev.wisteria.modules.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.wisteria.modules.xray.XrayLogic;
import dev.wisteria.setting.ActionSetting;
import dev.wisteria.setting.InfoSetting;
import dev.wisteria.setting.PasswordSetting;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * World seeds, one per server, kept in the active profile (its data folder, {@code seeds/seeds.json}): what Xray
 * Predicted generates ores from. A seed is typed only into the masked field here, never into chat, never shown again;
 * the chat guard holds back any line that contains one.
 */
public final class SeedsSection extends ClientSection {
	private static final String FILE = "seeds.json";
	/** Where each saved seed came from, when not typed by the player: server → source. */
	private static final String SOURCES = "sources.json";
	public static final String FROM_SEEDCRACKERX = "seedcrackerx";

	/** What to do with a seed another mod hands over. */
	public enum Offer {
		/** No seed yet for this server: save it. */
		SAVE,
		/** The same seed is already saved. */
		SAME,
		/** The saved one came from the same mod earlier: the newer result replaces it. */
		REPLACE,
		/** The player's own seed differs: keep it, and offer the new one in the menu. */
		ASK
	}

	private @Nullable Path cachedFor;
	private Map<String, Long> cached = new TreeMap<>();
	private Map<String, String> sources = new TreeMap<>();
	/** Seeds offered by another mod that differ from the player's own, until accepted or the game ends. */
	private final Map<String, Long> offered = new java.util.concurrent.ConcurrentHashMap<>();

	public SeedsSection() {
		super("Seeds", "World seeds per server for Xray Predicted; typed here only, never into chat");
		this.add(new InfoSetting("Server", "The server these controls apply to", () -> {
			String server = this.currentServer();
			return server == null ? "(not on a server)" : server;
		}));
		this.add(new PasswordSetting("Seed", "World seed of the server you are on: a number or the text it was made from. Never shown, never sent",
			() -> this.current().isPresent(), this::storeForCurrent));
		this.add(new ActionSetting("Use World Seed", "In single player: take this world's own seed", "Use", this::useWorldSeed)
			.withNote("saves a seed into the profile; covered by XrayGameTest"));
		this.add(new ActionSetting("Forget", "Delete the seed saved for this server", "Forget", () -> {
			if (this.currentServer() != null) {
				this.storeForCurrent("");
			}
		}));
		this.add(new InfoSetting("Saved", "How many servers have a seed in this profile", () -> Integer.toString(this.seeds().size())));
		this.add(new InfoSetting("Origin", "Where this server's seed came from", this::origin));
		this.add(new InfoSetting("SeedcrackerX", "If SeedcrackerX is installed, a seed it cracks is saved here for the server you are on",
			this::seedcrackerStatus));
		this.add(new ActionSetting("Use Offered Seed", "Replace your seed for this server with the one SeedcrackerX found", "Use",
			this::acceptOffer));
	}

	/** Pure: what to do when a mod hands over {@code offer} and {@code saved} (from {@code savedSource}) is on file. */
	public static Offer decide(@Nullable Long saved, @Nullable String savedSource, String source, long offer) {
		if (saved == null) {
			return Offer.SAVE;
		}

		if (saved == offer) {
			return Offer.SAME;
		}

		return source.equals(savedSource) ? Offer.REPLACE : Offer.ASK;
	}

	/**
	 * A seed handed over by another mod (SeedcrackerX) for the server the player is on. Client thread. Never overwrites
	 * a seed the player set without them confirming it in the menu.
	 *
	 * @return what was done
	 */
	public @Nullable Offer offer(long seed, String source) {
		String server = this.currentServer();
		if (server == null) {
			return null;
		}

		Offer decision = decide(this.seeds().get(server), this.sources.get(server), source, seed);
		switch (decision) {
			case SAVE, REPLACE -> {
				this.save(server, seed, source);
				this.client().hud().notify("Seeds", "Seed from SeedcrackerX saved for this server", dev.wisteria.hud.Toasts.Kind.INFO);
			}
			case ASK -> {
				this.offered.put(server, seed);
				this.client().hud().notify("Seeds", "SeedcrackerX found a different seed: Client > Seeds to use it", dev.wisteria.hud.Toasts.Kind.WARNING);
			}
			case SAME -> {
			}
		}

		return decision;
	}

	/** Where the current server's seed came from: null for none, "" for the player, else the mod's id. */
	public @Nullable String currentSource() {
		String server = this.currentServer();
		if (server == null || !this.seeds().containsKey(server)) {
			return null;
		}

		return this.sources.getOrDefault(server, "");
	}

	/** A seed waiting for confirmation for the current server. */
	public java.util.OptionalLong pendingOffer() {
		String server = this.currentServer();
		Long seed = server == null ? null : this.offered.get(server);
		return seed == null ? java.util.OptionalLong.empty() : java.util.OptionalLong.of(seed);
	}

	private String origin() {
		String source = this.currentSource();
		return source == null ? "(no seed)" : source.isEmpty() ? "set by you" : source.equals(FROM_SEEDCRACKERX) ? "from SeedcrackerX" : source;
	}

	private String seedcrackerStatus() {
		boolean installed;
		try {
			installed = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("seedcrackerx");
		} catch (RuntimeException | LinkageError noLoader) {
			installed = false;
		}

		if (this.pendingOffer().isPresent()) {
			return "found a different seed: Use Offered Seed to take it";
		}

		return installed ? "installed: a cracked seed is saved here" : "not installed";
	}

	private void acceptOffer() {
		String server = this.currentServer();
		Long seed = server == null ? null : this.offered.remove(server);
		if (seed != null) {
			this.save(server, seed, FROM_SEEDCRACKERX);
		}
	}

	/**
	 * The server the player is on, as seeds are filed: the address for a multiplayer server, {@code singleplayer/<world>}
	 * otherwise; null in the menu.
	 */
	public static @Nullable String serverOf(@Nullable Minecraft minecraft) {
		if (minecraft == null || minecraft.level == null) {
			return null;
		}

		MinecraftServer local = minecraft.getSingleplayerServer();
		if (local != null) {
			return "singleplayer/" + local.getWorldData().getLevelName();
		}

		ServerData server = minecraft.getCurrentServer();
		return server == null ? null : server.ip.strip().toLowerCase(Locale.ROOT);
	}

	public @Nullable String currentServer() {
		return serverOf(this.mc());
	}

	/** The seed saved for the server the player is on. */
	public OptionalLong current() {
		String server = this.currentServer();
		Long seed = server == null ? null : this.seeds().get(server);
		return seed == null ? OptionalLong.empty() : OptionalLong.of(seed);
	}

	/** Every saved seed as text, for the chat guard. */
	public List<String> seedTexts() {
		List<String> texts = new ArrayList<>();
		for (long seed : this.seeds().values()) {
			texts.add(Long.toString(seed));
		}

		return texts;
	}

	private void storeForCurrent(String text) {
		String server = this.currentServer();
		if (server == null) {
			throw new IllegalArgumentException("Join the server first: the seed is saved for the server you are on");
		}

		this.offered.remove(server);
		if (text.isBlank()) {
			Map<String, Long> seeds = new TreeMap<>(this.seeds());
			seeds.remove(server);
			this.sources.remove(server);
			this.save(seeds);
		} else {
			this.save(server, XrayLogic.parseSeed(text), null);
		}
	}

	/** Saves one server's seed, with where it came from (null: the player). */
	private void save(String server, long seed, @Nullable String source) {
		Map<String, Long> seeds = new TreeMap<>(this.seeds());
		seeds.put(server, seed);
		if (source == null) {
			this.sources.remove(server);
		} else {
			this.sources.put(server, source);
		}

		this.save(seeds);
	}

	private void useWorldSeed() {
		MinecraftServer local = this.mc() == null ? null : this.mc().getSingleplayerServer();
		if (local == null || this.currentServer() == null) {
			this.info("Use World Seed works in single player only; on a server, type the seed into the Seed field");
			return;
		}

		this.storeForCurrent(Long.toString(local.overworld().getSeed()));
		this.info("Seed saved for this world");
	}

	private Map<String, Long> seeds() {
		Path folder = this.storage().directory();
		if (!folder.equals(this.cachedFor)) {
			Map<String, Long> read = new TreeMap<>();
			this.storage().readJson(FILE).filter(JsonElement::isJsonObject).ifPresent(json -> {
				for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
					try {
						read.put(entry.getKey(), entry.getValue().getAsLong());
					} catch (RuntimeException broken) {
						// A hand-edited line that is not a number: skip it.
					}
				}
			});
			Map<String, String> from = new TreeMap<>();
			this.storage().readJson(SOURCES).filter(JsonElement::isJsonObject).ifPresent(json -> {
				for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
					if (entry.getValue().isJsonPrimitive()) {
						from.put(entry.getKey(), entry.getValue().getAsString());
					}
				}
			});
			this.cached = read;
			this.sources = from;
			this.cachedFor = folder;
		}

		return this.cached;
	}

	private void save(Map<String, Long> seeds) {
		JsonObject json = new JsonObject();
		seeds.forEach(json::addProperty);
		this.storage().writeJson(FILE, json);
		JsonObject from = new JsonObject();
		this.sources.keySet().retainAll(seeds.keySet());
		this.sources.forEach(from::addProperty);
		this.storage().writeJson(SOURCES, from);
		this.cached = seeds;
		this.cachedFor = this.storage().directory();
	}
}
