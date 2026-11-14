package dev.wisteria.modules.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Nether highways as geometry: the four axes from 0 0, the four diagonals, and square ring roads at chosen distances.
 * Pure functions over x and z, tested without the game.
 */
public final class HighwayLogic {
	/**
	 * The nearest highway to a point.
	 *
	 * @param name "+X axis", "-Z axis", "+X+Z diagonal", "ring 1000"
	 * @param offset how far off it you are, in blocks
	 * @param nearestX the closest point on it
	 * @param travelYaw the game's yaw (degrees, 0 = +Z, 90 = -X) along the highway, away from 0 0 (rings: clockwise)
	 */
	public record Nearest(String name, double offset, double nearestX, double nearestZ, float travelYaw) {
	}

	private HighwayLogic() {
	}

	/** The game's yaw for a direction in the x-z plane. */
	public static float yaw(double dx, double dz) {
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}

	/** Angle from {@code from} to {@code to}, -180..180; positive turns right. */
	public static float turn(float from, float to) {
		float delta = (to - from) % 360.0F;
		if (delta > 180.0F) {
			delta -= 360.0F;
		} else if (delta < -180.0F) {
			delta += 360.0F;
		}

		return delta;
	}

	/** Every highway's distance; the nearest is first. */
	public static @Nullable Nearest nearest(double x, double z, boolean axes, boolean diagonals, List<Integer> rings) {
		List<Nearest> all = new ArrayList<>();
		if (axes) {
			all.add(ray(x, z, 1, 0, "+X axis"));
			all.add(ray(x, z, -1, 0, "-X axis"));
			all.add(ray(x, z, 0, 1, "+Z axis"));
			all.add(ray(x, z, 0, -1, "-Z axis"));
		}

		if (diagonals) {
			all.add(ray(x, z, 1, 1, "+X+Z diagonal"));
			all.add(ray(x, z, 1, -1, "+X-Z diagonal"));
			all.add(ray(x, z, -1, 1, "-X+Z diagonal"));
			all.add(ray(x, z, -1, -1, "-X-Z diagonal"));
		}

		for (int radius : rings) {
			if (radius > 0) {
				all.add(ring(x, z, radius));
			}
		}

		return all.stream().min(java.util.Comparator.comparingDouble(Nearest::offset)).orElse(null);
	}

	/** A highway from 0 0 outward along (dx, dz). */
	static Nearest ray(double x, double z, int dx, int dz, String name) {
		double length = Math.hypot(dx, dz);
		double ux = dx / length;
		double uz = dz / length;
		double along = Math.max(0.0, x * ux + z * uz);
		double px = ux * along;
		double pz = uz * along;
		return new Nearest(name, Math.hypot(x - px, z - pz), px, pz, yaw(ux, uz));
	}

	/** A square ring road: every point with max(|x|, |z|) = radius. */
	static Nearest ring(double x, double z, int radius) {
		double ax = Math.abs(x);
		double az = Math.abs(z);
		double px;
		double pz;
		if (ax >= az) {
			px = Math.copySign(radius, x == 0 ? 1 : x);
			pz = Math.clamp(z, -radius, radius);
		} else {
			px = Math.clamp(x, -radius, radius);
			pz = Math.copySign(radius, z == 0 ? 1 : z);
		}

		// Clockwise seen from above (+Y): along +X on the north side, +Z on the east, -X on the south, -Z on the west.
		float travel;
		if (Math.abs(pz + radius) < 1e-9 && Math.abs(px) < radius) {
			travel = yaw(1, 0);
		} else if (Math.abs(px - radius) < 1e-9) {
			travel = yaw(0, 1);
		} else if (Math.abs(pz - radius) < 1e-9) {
			travel = yaw(-1, 0);
		} else {
			travel = yaw(0, -1);
		}

		return new Nearest("ring " + radius, Math.hypot(x - px, z - pz), px, pz, travel);
	}

	/** "1000 5000 10000" → the radii; anything that is not a positive whole number is skipped. */
	public static List<Integer> parseRings(String text) {
		List<Integer> rings = new ArrayList<>();
		for (String word : text.trim().split("[\\s,]+")) {
			try {
				int radius = Integer.parseInt(word);
				if (radius > 0) {
					rings.add(radius);
				}
			} catch (NumberFormatException skipped) {
				// not a number
			}
		}

		return rings;
	}

	/** The line under the name: "on it", or how far and which way to go to reach it. */
	public static String guidance(Nearest nearest, double x, double z, double tolerance) {
		if (nearest.offset() <= tolerance) {
			return "on it";
		}

		return String.format(Locale.ROOT, "%d off · go %s", Math.round(nearest.offset()), compass(yaw(nearest.nearestX() - x, nearest.nearestZ() - z)));
	}

	/** Eight-way compass word for a yaw: "N", "NE", … (north is -Z). */
	public static String compass(float yaw) {
		String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
		int index = Math.floorMod(Math.round(yaw / 45.0F), 8);
		return names[index];
	}
}
