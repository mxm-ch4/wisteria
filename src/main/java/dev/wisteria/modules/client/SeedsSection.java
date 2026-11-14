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

	private @Nullable Path cachedFor;
	private Map<String, Long> cached = new TreeMap<>();

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

		Map<String, Long> seeds = new TreeMap<>(this.seeds());
		if (text.isBlank()) {
			seeds.remove(server);
		} else {
			seeds.put(server, XrayLogic.parseSeed(text));
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
			this.cached = read;
			this.cachedFor = folder;
		}

		return this.cached;
	}

	private void save(Map<String, Long> seeds) {
		JsonObject json = new JsonObject();
		seeds.forEach(json::addProperty);
		this.storage().writeJson(FILE, json);
		this.cached = seeds;
		this.cachedFor = this.storage().directory();
	}
}
