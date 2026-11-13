package dev.wisteria.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.wisteria.Client;
import dev.wisteria.Wisteria;
import dev.wisteria.command.CommandManager;
import dev.wisteria.module.Module;
import dev.wisteria.setting.ColorSetting;
import dev.wisteria.setting.Setting;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Everything that survives a restart, as JSON under {@code .minecraft/wisteria/}:
 * <ul>
 * <li>{@code client.json}: the active profile, the command prefix, the theme and the hosts the user allowed;</li>
 * <li>{@code social.json}: friends and enemies, shared by all profiles;</li>
 * <li>{@code profiles/<name>.json}: which modules are on, their binds and settings.</li>
 * </ul>
 * Changes are written a couple of seconds after the last one and again on shutdown. A file that cannot be
 * read is set aside as {@code *.broken} and defaults are used, so a bad edit never stops the game from starting.
 */
public final class ConfigManager {
	public static final String DEFAULT_PROFILE = "default";
	private static final int SAVE_DELAY_TICKS = 40;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();

	private final Client client;
	private final Path directory;
	private String profile = DEFAULT_PROFILE;
	private boolean loading;
	private boolean dirty;
	private int ticksSinceChange;

	public ConfigManager(Client client, Path directory) {
		this.client = client;
		this.directory = directory;
	}

	public Path directory() {
		return this.directory;
	}

	public String profile() {
		return this.profile;
	}

	public static boolean isValidProfileName(String name) {
		return name.matches("[A-Za-z0-9_-]{1,32}");
	}

	private Path profileFile(String name) {
		return this.directory.resolve("profiles").resolve(name + ".json");
	}

	// ---- lifecycle ----

	/** Reads everything from disk. Missing files are fine: defaults stay and get written on the next save. */
	public void load() {
		this.loading = true;
		try {
			JsonObject clientJson = this.read(this.directory.resolve("client.json"));
			if (clientJson != null) {
				String name = string(clientJson, "profile");
				if (name != null && isValidProfileName(name)) {
					this.profile = name;
				}

				String prefix = string(clientJson, "prefix");
				if (prefix != null && CommandManager.isValidPrefix(prefix)) {
					this.client.commands().setPrefix(prefix);
				}

				Integer accent = null;
				String accentHex = string(clientJson, "accent");
				if (accentHex != null) {
					try {
						accent = ColorSetting.fromHex(accentHex);
					} catch (IllegalArgumentException ignored) {
						// keep the preset's own accent
					}
				}

				this.client.themes().load(string(clientJson, "theme"), accent);
				JsonObject hud = object(clientJson, "hud");
				if (hud != null) {
					this.client.hud().load(hud);
				}

				JsonObject net = object(clientJson, "net");
				if (net != null) {
					this.client.net().load(strings(net, "allowedHosts"));
				}

				JsonObject gui = object(clientJson, "gui");
				if (gui != null) {
					this.client.gui().load(gui);
				}
			}

			JsonObject social = this.read(this.directory.resolve("social.json"));
			if (social != null) {
				this.client.relations().load(strings(social, "friends"), strings(social, "enemies"));
			}

			JsonObject profileJson = this.read(this.profileFile(this.profile));
			if (profileJson != null) {
				this.applyProfile(profileJson);
			} else {
				this.dirty = true;
			}
		} finally {
			this.loading = false;
		}
	}

	/** Something changed; write it out once things settle. */
	public void markDirty() {
		if (!this.loading) {
			this.dirty = true;
			this.ticksSinceChange = 0;
		}
	}

	/** Call once per client tick. */
	public void tick() {
		if (this.dirty && ++this.ticksSinceChange >= SAVE_DELAY_TICKS) {
			this.save();
		}
	}

	/** Writes everything now. */
	public void save() {
		this.dirty = false;
		this.ticksSinceChange = 0;
		try {
			JsonObject clientJson = new JsonObject();
			clientJson.addProperty("profile", this.profile);
			clientJson.addProperty("prefix", this.client.commands().prefix());
			clientJson.addProperty("theme", this.client.themes().presetId());
			Integer accent = this.client.themes().customAccent();
			clientJson.addProperty("accent", accent != null ? ColorSetting.toHex(accent) : null);
			clientJson.add("gui", this.client.gui().toJson());
			clientJson.add("hud", this.client.hud().toJson());
			JsonObject net = new JsonObject();
			net.add("allowedHosts", array(this.client.net().allowedHosts()));
			clientJson.add("net", net);
			this.write(this.directory.resolve("client.json"), clientJson);

			JsonObject social = new JsonObject();
			social.add("friends", array(this.client.relations().friends()));
			social.add("enemies", array(this.client.relations().enemies()));
			this.write(this.directory.resolve("social.json"), social);

			this.write(this.profileFile(this.profile), this.snapshotProfile());
		} catch (IOException e) {
			Wisteria.LOG.error("Could not save config to {}", this.directory, e);
		}
	}

	// ---- profiles ----

	/** Saved profiles plus the active one, sorted. */
	public List<String> profiles() {
		TreeSet<String> names = new TreeSet<>();
		names.add(this.profile);
		Path profiles = this.directory.resolve("profiles");
		if (Files.isDirectory(profiles)) {
			try (Stream<Path> files = Files.list(profiles)) {
				files.map(file -> file.getFileName().toString())
					.filter(file -> file.endsWith(".json"))
					.map(file -> file.substring(0, file.length() - ".json".length()))
					.filter(ConfigManager::isValidProfileName)
					.forEach(names::add);
			} catch (IOException e) {
				Wisteria.LOG.warn("Could not list profiles in {}", profiles, e);
			}
		}

		return List.copyOf(names);
	}

	/** Saves the current state under a new name and makes that profile the active one. */
	public void saveAs(String name) {
		requireValid(name);
		this.save();
		this.profile = name;
		this.save();
	}

	/**
	 * Switches to a saved profile: the current one is saved first, then every module is reset and the
	 * other profile's state applied.
	 *
	 * @throws IllegalArgumentException if there is no such profile
	 */
	public void switchTo(String name) {
		requireValid(name);
		if (name.equals(this.profile)) {
			return;
		}

		if (!Files.isRegularFile(this.profileFile(name))) {
			throw new IllegalArgumentException("No profile called \"" + name + "\"");
		}

		this.save();
		JsonObject json = this.read(this.profileFile(name));
		this.profile = name;
		this.loading = true;
		try {
			this.applyProfile(json != null ? json : new JsonObject());
		} finally {
			this.loading = false;
		}

		this.save();
	}

	/** @throws IllegalArgumentException for the active profile or one that does not exist */
	public void delete(String name) {
		requireValid(name);
		if (name.equals(this.profile)) {
			throw new IllegalArgumentException("\"" + name + "\" is the active profile; switch to another one first");
		}

		try {
			if (!Files.deleteIfExists(this.profileFile(name))) {
				throw new IllegalArgumentException("No profile called \"" + name + "\"");
			}
		} catch (IOException e) {
			throw new IllegalArgumentException("Could not delete \"" + name + "\": " + e.getMessage());
		}
	}

	private static void requireValid(String name) {
		if (!isValidProfileName(name)) {
			throw new IllegalArgumentException("Profile names are 1-32 letters, digits, - or _");
		}
	}

	// ---- (de)serialisation ----

	private JsonObject snapshotProfile() {
		JsonObject modules = new JsonObject();
		for (Module module : this.client.modules().all()) {
			JsonObject json = new JsonObject();
			json.addProperty("enabled", module.isEnabled() && module.savesEnabled());
			json.add("bind", module.bind().toJson());
			JsonObject settings = new JsonObject();
			for (Setting<?> setting : module.settings()) {
				settings.add(setting.id(), setting.toJson());
			}

			json.add("settings", settings);
			modules.add(module.id(), json);
		}

		JsonObject root = new JsonObject();
		root.add("modules", modules);
		return root;
	}

	/** Resets every module, then applies what the profile says. Unknown modules and settings are skipped. */
	private void applyProfile(JsonObject root) {
		JsonObject modules = object(root, "modules");
		for (Module module : this.client.modules().all()) {
			module.reset();
			JsonObject json = modules != null ? object(modules, module.id()) : null;
			if (json == null) {
				continue;
			}

			JsonElement bind = json.get("bind");
			if (bind != null) {
				module.bind().fromJson(bind);
			}

			JsonObject settings = object(json, "settings");
			if (settings != null) {
				for (Map.Entry<String, JsonElement> entry : settings.entrySet()) {
					Setting<?> setting = module.setting(entry.getKey());
					if (setting != null && entry.getValue() != null) {
						setting.fromJson(entry.getValue());
					}
				}
			}

			// Settings first, so a module switches on already configured.
			JsonElement enabled = json.get("enabled");
			if (module.savesEnabled() && enabled != null && enabled.isJsonPrimitive() && enabled.getAsJsonPrimitive().isBoolean() && enabled.getAsBoolean()) {
				this.client.setEnabled(module, true);
			}
		}
	}

	private @Nullable JsonObject read(Path file) {
		if (!Files.isRegularFile(file)) {
			return null;
		}

		try {
			JsonElement json = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonElement.class);
			if (json != null && json.isJsonObject()) {
				return json.getAsJsonObject();
			}

			throw new JsonParseException("not a JSON object");
		} catch (IOException | JsonParseException e) {
			Path aside = file.resolveSibling(file.getFileName() + ".broken");
			Wisteria.LOG.warn("Could not read {} ({}); keeping it as {} and using defaults", file, e.getMessage(), aside.getFileName());
			try {
				Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException moveError) {
				Wisteria.LOG.warn("Could not set {} aside", file, moveError);
			}

			return null;
		}
	}

	// Write to a temporary file and move it into place, so a crash mid-write cannot leave half a config.
	private void write(Path file, JsonObject json) throws IOException {
		Files.createDirectories(file.getParent());
		Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(temporary, GSON.toJson(json) + "\n", StandardCharsets.UTF_8);
		try {
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static @Nullable String string(JsonObject json, String key) {
		JsonElement element = json.get(key);
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() ? element.getAsString() : null;
	}

	private static @Nullable JsonObject object(JsonObject json, String key) {
		JsonElement element = json.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	private static List<String> strings(JsonObject json, String key) {
		List<String> result = new ArrayList<>();
		JsonElement element = json.get(key);
		if (element != null && element.isJsonArray()) {
			for (JsonElement item : element.getAsJsonArray()) {
				if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
					result.add(item.getAsString());
				}
			}
		}

		return result;
	}

	private static JsonArray array(List<String> values) {
		JsonArray array = new JsonArray();
		values.forEach(array::add);
		return array;
	}
}
