package dev.wisteria.modules.esp;

import dev.wisteria.event.Events;
import dev.wisteria.module.Category;
import dev.wisteria.module.Module;
import dev.wisteria.platform.WorldBackend;
import dev.wisteria.render.Renderer3D;
import dev.wisteria.setting.BoolSetting;
import dev.wisteria.setting.ColorSetting;
import dev.wisteria.setting.DoubleSetting;
import dev.wisteria.setting.EnumSetting;
import dev.wisteria.setting.FloatSetting;
import dev.wisteria.setting.IntSetting;
import dev.wisteria.ui.Colors;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Highlights players, mobs, items and containers in any mix of four styles: a box, the real hitbox with the eyes
 * and gaze, just the corners, and chams (the model itself filled with colour). Colours follow who it is: player,
 * friend, enemy, mob, item, container. Purely visual: nothing is sent to the server.
 */
public final class EntityEsp extends Module {
	public enum ChamsMode {
		FLAT, RIM, OUTLINE
	}

	private static final int MAX_CONTAINERS = 512;

	// What to show.
	private final DoubleSetting range = this.add(new DoubleSetting("Range", "How far away things are highlighted, in blocks", 64.0, 8.0, 256.0, 1.0));
	private final BoolSetting players = this.add(new BoolSetting("Players", "Other players", true));
	private final BoolSetting hostile = this.add(new BoolSetting("Hostile", "Hostile mobs", true));
	private final BoolSetting passive = this.add(new BoolSetting("Passive", "Animals and other peaceful mobs", false));
	private final BoolSetting items = this.add(new BoolSetting("Items", "Dropped items", false));
	private final BoolSetting containers = this.add(new BoolSetting("Containers", "Chests, barrels, shulker boxes and the like (box and corners styles)", false));
	private final BoolSetting throughWalls = this.add(new BoolSetting("Through Walls", "Draw over terrain; off to let blocks hide them", true));

	// Styles, in any combination.
	private final BoolSetting box = this.add(new BoolSetting("Box", "Style: a box around each one", true));
	private final FloatSetting boxWidth = this.add(new FloatSetting("Box Width", "Box outline thickness", 2.0F, 0.5F, 6.0F, 0.5F));
	private final IntSetting boxFill = this.add(new IntSetting("Box Fill", "Opacity of the box faces, 0 for outline only", 40, 0, 255));
	private final BoolSetting hitbox = this.add(new BoolSetting("Hitbox", "Style: the real hitbox, eye level and gaze", false));
	private final FloatSetting hitboxWidth = this.add(new FloatSetting("Hitbox Width", "Hitbox line thickness", 1.0F, 0.5F, 4.0F, 0.5F));
	private final BoolSetting hitboxGaze = this.add(new BoolSetting("Hitbox Gaze", "Show where it is looking", true));
	private final BoolSetting corners = this.add(new BoolSetting("Corners", "Style: only the corners of the box", false));
	private final FloatSetting cornersWidth = this.add(new FloatSetting("Corners Width", "Corner stroke thickness", 2.0F, 0.5F, 6.0F, 0.5F));
	private final FloatSetting cornerLength = this.add(new FloatSetting("Corner Length", "How much of each edge a corner covers", 0.25F, 0.05F, 0.5F, 0.05F));
	private final BoolSetting chams = this.add(new BoolSetting("Chams", "Style: the model filled with colour (mobs and players)", false));
	private final EnumSetting<ChamsMode> chamsMode = this.add(new EnumSetting<>("Chams Mode", "Flat fill, glow at the edges, or an outline", ChamsMode.FLAT));
	private final IntSetting chamsOpacity = this.add(new IntSetting("Chams Opacity", "How solid the fill is", 120, 10, 255));
	private final BoolSetting chamsPulse = this.add(new BoolSetting("Chams Pulse", "Breathe the fill in and out", false));

	// Colours by who it is.
	private final ColorSetting playerColor = this.add(new ColorSetting("Player Color", "Other players", 0xFFB79CFF));
	private final ColorSetting friendColor = this.add(new ColorSetting("Friend Color", "Players on your friends list", 0xFF8CE0B5));
	private final ColorSetting enemyColor = this.add(new ColorSetting("Enemy Color", "Players on your enemies list", 0xFFFF7A93));
	private final ColorSetting hostileColor = this.add(new ColorSetting("Hostile Color", "Hostile mobs", 0xFFFFC97A));
	private final ColorSetting passiveColor = this.add(new ColorSetting("Passive Color", "Peaceful mobs", 0xFFE9D8FF));
	private final ColorSetting itemColor = this.add(new ColorSetting("Item Color", "Dropped items", 0xFF9A92AD));
	private final ColorSetting containerColor = this.add(new ColorSetting("Container Color", "Chests and other containers", 0xFFFFB86B));

	private final Map<EspGeometry.Role, ColorSetting> colours = new EnumMap<>(EspGeometry.Role.class);

	public EntityEsp() {
		super("ESP", "Highlights players, mobs, items and containers: box, hitbox, corners, chams", Category.RENDER);
		this.colours.put(EspGeometry.Role.PLAYER, this.playerColor);
		this.colours.put(EspGeometry.Role.FRIEND, this.friendColor);
		this.colours.put(EspGeometry.Role.ENEMY, this.enemyColor);
		this.colours.put(EspGeometry.Role.HOSTILE, this.hostileColor);
		this.colours.put(EspGeometry.Role.PASSIVE, this.passiveColor);
		this.colours.put(EspGeometry.Role.ITEM, this.itemColor);
		this.colours.put(EspGeometry.Role.CONTAINER, this.containerColor);
		this.on(Events.Render3D.class, this::render);
	}

	/** The role ESP gives an entity, or null if it is filtered out (kind switched off, or the player themself). */
	public EspGeometry.Role roleOf(WorldBackend.EntityKind kind, String name, boolean self) {
		if (self) {
			return null;
		}

		EspGeometry.Role role = EspGeometry.role(kind, this.client().relations().of(name));
		if (role == null) {
			return null;
		}

		return switch (role) {
			case PLAYER, FRIEND, ENEMY -> this.players.get() ? role : null;
			case HOSTILE -> this.hostile.get() ? role : null;
			case PASSIVE -> this.passive.get() ? role : null;
			case ITEM -> this.items.get() ? role : null;
			case CONTAINER -> this.containers.get() ? role : null;
		};
	}

	public int colorOf(EspGeometry.Role role) {
		return this.colours.get(role).get();
	}

	/**
	 * For the chams hook in the entity renderer: the fill colour (alpha included, pulse applied) for this entity, or 0
	 * when it gets no chams.
	 */
	public int chamsColor(WorldBackend.EntityKind kind, String name, boolean self, double distance) {
		if (!this.isEnabled() || !this.chams.get() || distance > this.range.get()) {
			return 0;
		}

		EspGeometry.Role role = this.roleOf(kind, name, self);
		if (role == null || role == EspGeometry.Role.ITEM) {
			return 0;
		}

		int alpha = EspGeometry.pulse(this.chamsOpacity.get(), this.chamsPulse.get(), System.currentTimeMillis());
		return Colors.withAlpha(this.colorOf(role), Math.max(1, Math.min(255, alpha)));
	}

	public ChamsMode chamsMode() {
		return this.chamsMode.get();
	}

	public boolean throughWalls() {
		return this.throughWalls.get();
	}

	private void render(Events.Render3D event) {
		WorldBackend world = this.client().platform().world();
		if (!world.isLoaded()) {
			return;
		}

		Renderer3D renderer = event.renderer();
		double camX = renderer.cameraX();
		double camY = renderer.cameraY();
		double camZ = renderer.cameraZ();
		double maxDistance = this.range.get();
		for (WorldBackend.Entity entity : world.entities(event.tickDelta())) {
			if (entity.distanceTo(camX, camY, camZ) > maxDistance) {
				continue;
			}

			EspGeometry.Role role = this.roleOf(entity.kind(), entity.name(), entity.self());
			if (role == null) {
				continue;
			}

			double half = entity.width() / 2.0;
			this.styles(renderer, this.colorOf(role), entity.x() - half, entity.y(), entity.z() - half,
				entity.x() + half, entity.y() + entity.height(), entity.z() + half, entity);
		}

		if (this.containers.get()) {
			int colour = this.colorOf(EspGeometry.Role.CONTAINER);
			for (WorldBackend.Container container : world.containers(camX, camY, camZ, maxDistance, MAX_CONTAINERS)) {
				this.styles(renderer, colour, container.x() + 0.0625, container.y(), container.z() + 0.0625,
					container.x() + 0.9375, container.y() + 0.875, container.z() + 0.9375, null);
			}
		}
	}

	private void styles(Renderer3D renderer, int colour, double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
		WorldBackend.Entity entity) {
		boolean walls = this.throughWalls.get();
		if (this.box.get()) {
			int fill = this.boxFill.get();
			renderer.box(minX, minY, minZ, maxX, maxY, maxZ, colour, this.boxWidth.get(),
				fill == 0 ? 0 : Colors.withAlpha(colour, fill * Colors.alpha(colour) / 255), walls);
		}

		if (this.hitbox.get() && entity != null) {
			float width = this.hitboxWidth.get();
			renderer.box(minX, minY, minZ, maxX, maxY, maxZ, colour, width, 0, walls);
			double eyeY = entity.y() + entity.eyeHeight();
			this.lines(renderer, EspGeometry.eyeLevel(minX, minZ, maxX, maxZ, eyeY), Colors.mix(colour, 0xFFFF5A5A, 0.6F), width, walls);
			if (this.hitboxGaze.get()) {
				this.lines(renderer, List.of(EspGeometry.lookLine(entity.x(), eyeY, entity.z(), entity.lookX(), entity.lookY(), entity.lookZ(), 2.0)),
					Colors.mix(colour, 0xFF5A8CFF, 0.6F), width, walls);
			}
		}

		if (this.corners.get()) {
			this.lines(renderer, EspGeometry.corners(minX, minY, minZ, maxX, maxY, maxZ, this.cornerLength.get()), colour, this.cornersWidth.get(), walls);
		}
	}

	private void lines(Renderer3D renderer, List<double[]> segments, int colour, float width, boolean walls) {
		for (double[] s : segments) {
			renderer.line(s[0], s[1], s[2], s[3], s[4], s[5], colour, width, walls);
		}
	}
}
