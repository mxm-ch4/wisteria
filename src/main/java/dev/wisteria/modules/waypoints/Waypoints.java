package dev.wisteria.modules.waypoints;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.wisteria.event.Events;
import dev.wisteria.module.Category;
import dev.wisteria.module.Module;
import dev.wisteria.modules.client.SeedsSection;
import dev.wisteria.render.Renderer2D;
import dev.wisteria.setting.ActionSetting;
import dev.wisteria.setting.BoolSetting;
import dev.wisteria.setting.ChoiceSetting;
import dev.wisteria.setting.ColorSetting;
import dev.wisteria.setting.DoubleSetting;
import dev.wisteria.setting.InputSetting;
import dev.wisteria.setting.IntSetting;
import dev.wisteria.setting.ListSetting;
import dev.wisteria.ui.Colors;
import dev.wisteria.ui.Metrics;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Named points per server: a beam and a label in the world (the distance with it), a list in the menu, groups,
 * a mark where you died, Overworld and Nether shown in each other at 8:1, export to a file. Other modules add theirs
 * through {@link #add}. Passive: nothing is sent to the server.
 */
public final class Waypoints extends Module {
	private static final String ALL = "all";
	private static final int DEFAULT_COLOUR = 0xFFB79CFF;

	private final ListSetting list = this.add(new ListSetting("Waypoints",
		"Add as: name [x y z | x z] [@group]; without coordinates, where you stand. Remove with its button",
		"name x y z @group", this::lines, this::addTyped, line -> this.remove(WaypointLogic.nameOf(line))));
	private final InputSetting here = this.add(new InputSetting("Add Here", "A waypoint where you stand: name, @group optional", "name @group", "Add",
		this::addTyped));
	private final ChoiceSetting group = this.add(new ChoiceSetting("Group", "Show only this group", ALL, this::groups, true));
	private final ColorSetting colour = this.add(new ColorSetting("Color", "Colour of new waypoints", DEFAULT_COLOUR));
	private final DoubleSetting range = this.add(new DoubleSetting("Range", "Show waypoints this close, in blocks", 100_000.0, 16.0, 100_000.0, 16.0));
	private final BoolSetting beams = this.add(new BoolSetting("Beams", "A tall line over each waypoint, through walls", true));
	private final BoolSetting labels = this.add(new BoolSetting("Labels", "Name and distance on screen", true));
	private final BoolSetting otherDimension = this.add(new BoolSetting("Other Dimension",
		"Show Overworld waypoints in the Nether and the other way round, at 8:1", true));
	private final BoolSetting deaths = this.add(new BoolSetting("Death Waypoint", "Mark where you die, in the group \"deaths\"", true));
	private final IntSetting keepDeaths = this.add(new IntSetting("Keep Deaths", "How many death marks to keep", 5, 1, 50));

	private final List<Waypoint> waypoints = new ArrayList<>();
	private @Nullable String server;

	public Waypoints() {
		super("Waypoints", "Named points per server: beams, labels with distance, groups, death marks, export", Category.WORLD);
		this.add(new ActionSetting("Clear Deaths", "Remove every death mark on this server", "Clear", () -> {
			this.waypoints.removeIf(waypoint -> waypoint.group().equals(Waypoint.DEATHS));
			this.save();
		}));
		this.add(new ActionSetting("Export", "Write this server's waypoints to a JSON file in the profile's exports folder", "Export", this::export));
		this.on(Events.Join.class, event -> this.load());
		this.on(Events.Leave.class, event -> {
			this.server = null;
			this.waypoints.clear();
		});
		this.on(Events.Death.class, this::died);
		this.on(Events.Render3D.class, this::render3d);
		this.on(Events.Render2D.class, this::render2d);
	}

	@Override
	protected void onEnable() {
		this.load();
	}

	@Override
	protected void onDisable() {
		this.server = null;
		this.waypoints.clear();
	}

	@Override
	protected void onDataCleared() {
		this.server = null;
		this.waypoints.clear();
	}

	// ----- For other modules and tests -----

	/** Every waypoint on this server. */
	public List<Waypoint> all() {
		this.ensureLoaded();
		return List.copyOf(this.waypoints);
	}

	/**
	 * Adds (or moves, if the name exists) a waypoint on the current server, in the dimension the player is in. Client
	 * thread. Works while the module is off too, so BaseFinder can mark things for later.
	 *
	 * @throws IllegalArgumentException when not in a world, or for a bad name
	 */
	public Waypoint add(String name, int x, int y, int z, String inGroup) {
		ClientLevel level = this.level();
		if (level == null || !this.ensureLoaded()) {
			throw new IllegalArgumentException("Join a world first: waypoints are kept per server");
		}

		String trimmed = name.strip();
		if (trimmed.isEmpty() || trimmed.length() > WaypointLogic.MAX_NAME || trimmed.contains("·")) {
			throw new IllegalArgumentException("A name is 1 to " + WaypointLogic.MAX_NAME + " characters, without ·");
		}

		this.waypoints.removeIf(waypoint -> waypoint.name().equalsIgnoreCase(trimmed));
		Waypoint waypoint = new Waypoint(trimmed, x, y, z, level.dimension().identifier().toString(), inGroup.toLowerCase(Locale.ROOT),
			this.colour.get(), System.currentTimeMillis());
		this.waypoints.add(waypoint);
		this.save();
		return waypoint;
	}

	public boolean remove(String name) {
		this.ensureLoaded();
		boolean removed = this.waypoints.removeIf(waypoint -> waypoint.name().equalsIgnoreCase(name.strip()));
		if (removed) {
			this.save();
		}

		return removed;
	}

	private void addTyped(String text) {
		WaypointLogic.Parsed parsed = WaypointLogic.parse(text);
		LocalPlayer player = this.player();
		if (player == null) {
			throw new IllegalArgumentException("Join a world first: waypoints are kept per server");
		}

		int x = parsed.x() != null ? parsed.x() : player.blockPosition().getX();
		int y = parsed.y() != null ? parsed.y() : player.blockPosition().getY();
		int z = parsed.z() != null ? parsed.z() : player.blockPosition().getZ();
		this.add(parsed.name(), x, y, z, parsed.group());
	}

	private void died(Events.Death event) {
		if (!this.deaths.get() || !this.ensureLoaded()) {
			return;
		}

		int number = 1 + (int) this.waypoints.stream().filter(waypoint -> waypoint.group().equals(Waypoint.DEATHS)).count();
		ClientLevel level = this.level();
		String dimension = event.dimension().isEmpty() && level != null ? level.dimension().identifier().toString() : event.dimension();
		this.waypoints.add(new Waypoint("Death " + number, (int) Math.floor(event.position().x), (int) Math.floor(event.position().y),
			(int) Math.floor(event.position().z), dimension, Waypoint.DEATHS, 0xFFFF7A93, System.currentTimeMillis()));
		this.waypoints.retainAll(WaypointLogic.trimDeaths(this.waypoints, this.keepDeaths.get()));
		this.save();
	}

	private List<String> lines() {
		this.ensureLoaded();
		return this.waypoints.stream().map(WaypointLogic::line).toList();
	}

	private List<String> groups() {
		TreeSet<String> names = new TreeSet<>();
		for (Waypoint waypoint : this.waypoints) {
			if (!waypoint.group().isEmpty()) {
				names.add(waypoint.group());
			}
		}

		List<String> options = new ArrayList<>();
		options.add(ALL);
		options.addAll(names);
		return options;
	}

	/** What is shown now: this dimension (and the other at 8:1), the chosen group, within range; nearest first. */
	private List<Shown> shown() {
		ClientLevel level = this.level();
		LocalPlayer player = this.player();
		if (level == null || player == null) {
			return List.of();
		}

		String dimension = level.dimension().identifier().toString();
		List<Shown> result = new ArrayList<>();
		for (Waypoint waypoint : WaypointLogic.nearest(this.waypoints, dimension, this.otherDimension.get(), player.getX(), player.getZ())) {
			if (!this.group.get().equals(ALL) && !waypoint.group().equals(this.group.get())) {
				continue;
			}

			double[] at = WaypointLogic.positionIn(waypoint, dimension, this.otherDimension.get());
			double distance = WaypointLogic.distance(at, player.getX(), player.getZ());
			if (distance <= this.range.get()) {
				result.add(new Shown(waypoint, at, distance, !waypoint.dimension().equals(dimension)));
			}
		}

		return result;
	}

	private record Shown(Waypoint waypoint, double[] at, double distance, boolean converted) {
	}

	private void render3d(Events.Render3D event) {
		if (!this.beams.get()) {
			return;
		}

		for (Shown shown : this.shown()) {
			double x = shown.at()[0];
			double y = shown.at()[1];
			double z = shown.at()[2];
			int colour = shown.waypoint().colour();
			event.renderer().line(x + 0.5, y, z + 0.5, x + 0.5, y + 256.0, z + 0.5, colour, 2.0F, true);
			event.renderer().box(x, y, z, x + 1.0, y + 1.0, z + 1.0, colour, 1.5F, (0x40 << 24) | (colour & 0xFFFFFF), true);
		}
	}

	private void render2d(Events.Render2D event) {
		if (!this.labels.get()) {
			return;
		}

		Renderer2D renderer = event.renderer();
		var theme = this.client().themes().current();
		for (Shown shown : this.shown()) {
			float[] screen = this.platform().visuals().project(shown.at()[0] + 0.5, shown.at()[1] + 1.4, shown.at()[2] + 0.5);
			if (screen == null) {
				continue;
			}

			String text = shown.waypoint().name() + "  " + WaypointLogic.distanceText(shown.distance()) + (shown.converted() ? " (8:1)" : "");
			float width = Metrics.Type.LABEL.width(renderer, text) + 10.0F;
			float height = Metrics.Type.LABEL.lineHeight(renderer) + 4.0F;
			float left = screen[0] - width / 2.0F;
			float top = screen[1] - height;
			renderer.roundedRect(left, top, width, height, 4.0F, Colors.withAlpha(theme.surface(), 0xD0));
			renderer.roundedRect(left, top, 3.0F, height, 1.5F, shown.waypoint().colour());
			Metrics.Type.LABEL.draw(renderer, text, left + 6.0F, top + 2.0F, theme.text());
		}
	}

	// ----- Storage: per server, in the profile -----

	/** Loads the current server's waypoints if not already; false when not on a server. */
	private boolean ensureLoaded() {
		String now = SeedsSection.serverOf(this.mc());
		if (now == null) {
			return false;
		}

		if (!now.equals(this.server)) {
			this.server = now;
			this.read();
		}

		return true;
	}

	private void load() {
		this.server = null;
		this.ensureLoaded();
	}

	private static String fileFor(String serverId) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(serverId.getBytes(StandardCharsets.UTF_8));
			return "servers/" + HexFormat.of().formatHex(digest, 0, 8) + ".json";
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException(impossible);
		}
	}

	private void read() {
		this.waypoints.clear();
		String serverId = this.server;
		if (serverId == null) {
			return;
		}

		this.storage().readJson(fileFor(serverId)).filter(JsonElement::isJsonObject).ifPresent(json -> {
			JsonArray array = json.getAsJsonObject().getAsJsonArray("waypoints");
			if (array == null) {
				return;
			}

			for (JsonElement element : array) {
				try {
					JsonObject one = element.getAsJsonObject();
					this.waypoints.add(new Waypoint(one.get("name").getAsString(), one.get("x").getAsInt(), one.get("y").getAsInt(),
						one.get("z").getAsInt(), one.get("dimension").getAsString(), one.has("group") ? one.get("group").getAsString() : "",
						one.has("colour") ? one.get("colour").getAsInt() : DEFAULT_COLOUR, one.has("created") ? one.get("created").getAsLong() : 0L));
				} catch (RuntimeException broken) {
					// a damaged entry: skip it
				}
			}
		});
	}

	private JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("server", this.server);
		JsonArray array = new JsonArray();
		for (Waypoint waypoint : this.waypoints) {
			JsonObject one = new JsonObject();
			one.addProperty("name", waypoint.name());
			one.addProperty("x", waypoint.x());
			one.addProperty("y", waypoint.y());
			one.addProperty("z", waypoint.z());
			one.addProperty("dimension", waypoint.dimension());
			one.addProperty("group", waypoint.group());
			one.addProperty("colour", waypoint.colour());
			one.addProperty("created", waypoint.created());
			array.add(one);
		}

		json.add("waypoints", array);
		return json;
	}

	private void save() {
		if (this.server != null) {
			this.storage().writeJson(fileFor(this.server), this.toJson());
		}
	}

	private void export() {
		if (!this.ensureLoaded()) {
			this.info("Join a world first: waypoints are kept per server");
			return;
		}

		String name = "waypoints-" + fileFor(this.server).substring("servers/".length());
		var path = this.storage().export(name, this.toJson().toString().getBytes(StandardCharsets.UTF_8));
		this.info(this.waypoints.size() + " waypoints exported to " + path.getFileName());
	}
}
