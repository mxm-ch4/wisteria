package dev.wisteria.modules.esp;

import dev.wisteria.platform.WorldBackend;
import dev.wisteria.social.Relations;
import java.util.ArrayList;
import java.util.List;

/** What ESP draws, as pure functions of plain numbers: who gets which colour, and the lines of each style. */
public final class EspGeometry {
	private EspGeometry() {
	}

	/** Whose colour an entity gets. */
	public enum Role {
		PLAYER, FRIEND, ENEMY, HOSTILE, PASSIVE, ITEM, CONTAINER
	}

	/** The role of an entity, or null for kinds ESP does not show. */
	public static Role role(WorldBackend.EntityKind kind, Relations.Relation relation) {
		return switch (kind) {
			case PLAYER -> relation == Relations.Relation.FRIEND ? Role.FRIEND : relation == Relations.Relation.ENEMY ? Role.ENEMY : Role.PLAYER;
			case HOSTILE -> Role.HOSTILE;
			case PASSIVE -> Role.PASSIVE;
			case ITEM -> Role.ITEM;
			case OTHER -> null;
		};
	}

	/**
	 * The corners of a box: three short strokes from each of its eight corners along the edges.
	 *
	 * @param fraction how much of each edge a stroke covers, 0.05 to 0.5
	 * @return segments {x1, y1, z1, x2, y2, z2}
	 */
	public static List<double[]> corners(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double fraction) {
		double f = Math.max(0.05, Math.min(0.5, fraction));
		double lx = (maxX - minX) * f;
		double ly = (maxY - minY) * f;
		double lz = (maxZ - minZ) * f;
		List<double[]> segments = new ArrayList<>(24);
		for (int corner = 0; corner < 8; corner++) {
			double x = (corner & 1) == 0 ? minX : maxX;
			double y = (corner & 2) == 0 ? minY : maxY;
			double z = (corner & 4) == 0 ? minZ : maxZ;
			double sx = (corner & 1) == 0 ? 1.0 : -1.0;
			double sy = (corner & 2) == 0 ? 1.0 : -1.0;
			double sz = (corner & 4) == 0 ? 1.0 : -1.0;
			segments.add(new double[] {x, y, z, x + sx * lx, y, z});
			segments.add(new double[] {x, y, z, x, y + sy * ly, z});
			segments.add(new double[] {x, y, z, x, y, z + sz * lz});
		}

		return segments;
	}

	/** A flat square around the hitbox at eye height, the way the game's own hitbox view marks the eyes. */
	public static List<double[]> eyeLevel(double minX, double minZ, double maxX, double maxZ, double eyeY) {
		return List.of(
			new double[] {minX, eyeY, minZ, maxX, eyeY, minZ},
			new double[] {maxX, eyeY, minZ, maxX, eyeY, maxZ},
			new double[] {maxX, eyeY, maxZ, minX, eyeY, maxZ},
			new double[] {minX, eyeY, maxZ, minX, eyeY, minZ});
	}

	/** Where the entity looks: from the eyes, {@code length} blocks along its gaze. */
	public static double[] lookLine(double eyeX, double eyeY, double eyeZ, double lookX, double lookY, double lookZ, double length) {
		double norm = Math.sqrt(lookX * lookX + lookY * lookY + lookZ * lookZ);
		if (norm < 1e-9) {
			return new double[] {eyeX, eyeY, eyeZ, eyeX, eyeY, eyeZ};
		}

		return new double[] {eyeX, eyeY, eyeZ, eyeX + lookX / norm * length, eyeY + lookY / norm * length, eyeZ + lookZ / norm * length};
	}

	/** Chams opacity this moment: steady, or breathing between 40% and 100% once a second and a half. */
	public static int pulse(int alpha, boolean pulse, long millis) {
		if (!pulse) {
			return alpha;
		}

		double wave = 0.7 + 0.3 * Math.sin(millis / 1500.0 * Math.PI * 2.0);
		return (int)Math.round(alpha * wave);
	}
}
