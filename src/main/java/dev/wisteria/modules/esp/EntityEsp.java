package dev.wisteria.modules.esp;

import dev.wisteria.render.Renderer3D;
import dev.wisteria.platform.WorldBackend;
import dev.wisteria.event.Events;
import dev.wisteria.module.Category;
import dev.wisteria.module.Module;
import dev.wisteria.setting.BoolSetting;
import dev.wisteria.setting.ColorSetting;
import dev.wisteria.setting.DoubleSetting;
import dev.wisteria.setting.FloatSetting;
import dev.wisteria.setting.IntSetting;
import dev.wisteria.social.Relations;

/** Boxes around entities, through walls if asked. Purely visual: nothing is sent to the server. */
public final class EntityEsp extends Module {
	private final DoubleSetting range = this.add(new DoubleSetting("Range", "How far away entities get a box, in blocks", 64.0, 8.0, 256.0, 1.0));
	private final BoolSetting players = this.add(new BoolSetting("Players", "Box other players", true));
	private final BoolSetting hostile = this.add(new BoolSetting("Hostile", "Box hostile mobs", true));
	private final BoolSetting passive = this.add(new BoolSetting("Passive", "Box animals and other peaceful mobs", false));
	private final BoolSetting items = this.add(new BoolSetting("Items", "Box dropped items", false));
	private final BoolSetting throughWalls = this.add(new BoolSetting("Through Walls", "Draw boxes over terrain", true));
	private final FloatSetting lineWidth = this.add(new FloatSetting("Line Width", "Outline thickness", 2.0F, 0.5F, 6.0F, 0.5F));
	private final IntSetting fill = this.add(new IntSetting("Fill", "Opacity of the box faces, 0 for outline only", 40, 0, 255));
	private final ColorSetting playerColor = this.add(new ColorSetting("Player Color", "Other players", 0xFFB79CFF));
	private final ColorSetting friendColor = this.add(new ColorSetting("Friend Color", "Players on your friends list", 0xFF8CE0B5));
	private final ColorSetting enemyColor = this.add(new ColorSetting("Enemy Color", "Players on your enemies list", 0xFFFF7A93));
	private final ColorSetting hostileColor = this.add(new ColorSetting("Hostile Color", "Hostile mobs", 0xFFFFC97A));
	private final ColorSetting passiveColor = this.add(new ColorSetting("Passive Color", "Peaceful mobs", 0xFFE9D8FF));
	private final ColorSetting itemColor = this.add(new ColorSetting("Item Color", "Dropped items", 0xFF9A92AD));

	public EntityEsp() {
		super("ESP", "Highlights players, mobs and items with boxes", Category.RENDER);
		this.on(Events.Render3D.class, this::render);
	}

	private void render(Events.Render3D event) {
		WorldBackend world = this.client().platform().world();
		if (!world.isLoaded()) {
			return;
		}

		Renderer3D renderer = event.renderer();
		double maxDistance = this.range.get();
		boolean walls = this.throughWalls.get();
		float width = this.lineWidth.get();
		int fillAlpha = this.fill.get();
		for (WorldBackend.Entity entity : world.entities(event.tickDelta())) {
			if (entity.self() || entity.distanceTo(renderer.cameraX(), renderer.cameraY(), renderer.cameraZ()) > maxDistance) {
				continue;
			}

			ColorSetting color = this.colorFor(entity);
			if (color == null) {
				continue;
			}

			double half = entity.width() / 2.0;
			renderer.box(
				entity.x() - half, entity.y(), entity.z() - half,
				entity.x() + half, entity.y() + entity.height(), entity.z() + half,
				color.get(), width, fillAlpha == 0 ? 0 : color.withAlpha(fillAlpha * color.alpha() / 255), walls
			);
		}
	}

	private ColorSetting colorFor(WorldBackend.Entity entity) {
		return switch (entity.kind()) {
			case PLAYER -> {
				if (!this.players.get()) {
					yield null;
				}

				Relations.Relation relation = this.client().relations().of(entity.name());
				yield relation == Relations.Relation.FRIEND ? this.friendColor
					: relation == Relations.Relation.ENEMY ? this.enemyColor : this.playerColor;
			}
			case HOSTILE -> this.hostile.get() ? this.hostileColor : null;
			case PASSIVE -> this.passive.get() ? this.passiveColor : null;
			case ITEM -> this.items.get() ? this.itemColor : null;
			case OTHER -> null;
		};
	}
}
