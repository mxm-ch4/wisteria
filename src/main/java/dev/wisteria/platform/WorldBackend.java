package dev.wisteria.platform;

import java.util.List;

/** What the client can see of the world it is in. Client thread only. */
public interface WorldBackend {
	/** True while the player is in a world. */
	boolean isLoaded();

	/**
	 * Every entity the client currently knows about, the player included ({@link Entity#self()}).
	 * Positions are interpolated for the given partial tick, so boxes follow smoothly between ticks.
	 */
	List<Entity> entities(float tickDelta);

	enum EntityKind {
		PLAYER, HOSTILE, PASSIVE, ITEM, OTHER
	}

	/**
	 * A snapshot of one entity for this frame.
	 *
	 * @param name the profile name for players, the display name otherwise
	 * @param x feet position, centre of the hitbox
	 * @param self true for the player themself
	 */
	record Entity(int id, EntityKind kind, String name, double x, double y, double z, float width, float height, boolean self) {
		public double distanceTo(double px, double py, double pz) {
			double dx = this.x - px;
			double dy = this.y - py;
			double dz = this.z - pz;
			return Math.sqrt(dx * dx + dy * dy + dz * dz);
		}
	}
}
