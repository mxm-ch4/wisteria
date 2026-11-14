package dev.wisteria.modules.waypoints;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** Waypoints as plain data: parsing what is typed, where a point shows in each dimension, the list lines. */
public final class WaypointLogic {
	public static final int MAX_NAME = 32;

	/** What a typed line asks for: a name, an optional group, and coordinates or "here". */
	public record Parsed(String name, String group, @Nullable Integer x, @Nullable Integer y, @Nullable Integer z) {
	}

	private WaypointLogic() {
	}

	/**
	 * "Base" (here), "Base 100 -200" (x z, height here), "Base 100 64 -200"; a word starting with @ is the group:
	 * "Base 100 64 -200 @home".
	 *
	 * @throws IllegalArgumentException with what is wrong
	 */
	public static Parsed parse(String text) {
		List<String> words = new ArrayList<>(List.of(text.strip().split("\\s+")));
		String group = "";
		for (int i = words.size() - 1; i >= 0; i--) {
			if (words.get(i).startsWith("@") && words.get(i).length() > 1) {
				group = words.remove(i).substring(1).toLowerCase(Locale.ROOT);
			}
		}

		List<Integer> numbers = new ArrayList<>();
		while (!words.isEmpty() && numbers.size() < 3 && isInt(words.getLast())) {
			numbers.addFirst(Integer.parseInt(words.removeLast()));
		}

		if (numbers.size() == 1) {
			// "Farm 2": one number is part of the name, not a coordinate.
			words.add(Integer.toString(numbers.removeFirst()));
		}

		String name = String.join(" ", words).strip();
		if (name.isEmpty() && !numbers.isEmpty() && words.isEmpty()) {
			throw new IllegalArgumentException("Give the waypoint a name before its coordinates");
		}

		if (name.isEmpty()) {
			throw new IllegalArgumentException("Type a name, then x y z or x z (none: where you stand), @group optional");
		}

		if (name.length() > MAX_NAME || name.contains("·")) {
			throw new IllegalArgumentException("A name is 1 to " + MAX_NAME + " characters, without ·");
		}

		return switch (numbers.size()) {
			case 0 -> new Parsed(name, group, null, null, null);
			case 2 -> new Parsed(name, group, numbers.get(0), null, numbers.get(1));
			case 3 -> new Parsed(name, group, numbers.get(0), numbers.get(1), numbers.get(2));
			default -> throw new IllegalArgumentException("Coordinates are x y z or x z");
		};
	}

	private static boolean isInt(String word) {
		try {
			Integer.parseInt(word);
			return true;
		} catch (NumberFormatException notANumber) {
			return false;
		}
	}

	/**
	 * Where a waypoint shows in a dimension: its own spot in its own dimension; between the Overworld and the Nether
	 * scaled 8:1 (if {@code crossDimension}); elsewhere nowhere (null). Block coordinates, x y z.
	 */
	public static double @Nullable [] positionIn(Waypoint waypoint, String dimension, boolean crossDimension) {
		if (waypoint.dimension().equals(dimension)) {
			return new double[] {waypoint.x(), waypoint.y(), waypoint.z()};
		}

		if (!crossDimension) {
			return null;
		}

		if (waypoint.dimension().equals(Waypoint.OVERWORLD) && dimension.equals(Waypoint.NETHER)) {
			return new double[] {Math.floor(waypoint.x() / 8.0), waypoint.y(), Math.floor(waypoint.z() / 8.0)};
		}

		if (waypoint.dimension().equals(Waypoint.NETHER) && dimension.equals(Waypoint.OVERWORLD)) {
			return new double[] {waypoint.x() * 8.0, waypoint.y(), waypoint.z() * 8.0};
		}

		return null;
	}

	/** Horizontal distance from a spot to the middle of a block. */
	public static double distance(double[] block, double fromX, double fromZ) {
		return Math.hypot(block[0] + 0.5 - fromX, block[2] + 0.5 - fromZ);
	}

	/** "1.2 km", "340 m". */
	public static String distanceText(double blocks) {
		return blocks >= 1000.0 ? String.format(Locale.ROOT, "%.1f km", blocks / 1000.0) : Math.round(blocks) + " m";
	}

	/** The menu line: "Base · 100 64 -200 · overworld · @home". */
	public static String line(Waypoint waypoint) {
		String dimension = waypoint.dimension().substring(waypoint.dimension().indexOf(':') + 1);
		return waypoint.name() + " · " + waypoint.x() + " " + waypoint.y() + " " + waypoint.z() + " · " + dimension
			+ (waypoint.group().isEmpty() ? "" : " · @" + waypoint.group());
	}

	/** The name at the start of a menu line. */
	public static String nameOf(String line) {
		int cut = line.indexOf(" · ");
		return (cut < 0 ? line : line.substring(0, cut)).strip();
	}

	/** Nearest first, by where they show in {@code dimension}; those that do not show there are left out. */
	public static List<Waypoint> nearest(List<Waypoint> all, String dimension, boolean crossDimension, double x, double z) {
		return all.stream().filter(waypoint -> positionIn(waypoint, dimension, crossDimension) != null)
			.sorted(Comparator.comparingDouble(waypoint -> distance(positionIn(waypoint, dimension, crossDimension), x, z))).toList();
	}

	/** Keeps the newest {@code keep} death marks, drops the rest. */
	public static List<Waypoint> trimDeaths(List<Waypoint> all, int keep) {
		List<Waypoint> deaths = all.stream().filter(waypoint -> waypoint.group().equals(Waypoint.DEATHS))
			.sorted(Comparator.comparingLong(Waypoint::created).reversed()).toList();
		if (deaths.size() <= keep) {
			return all;
		}

		List<Waypoint> drop = deaths.subList(keep, deaths.size());
		return all.stream().filter(waypoint -> !drop.contains(waypoint)).toList();
	}
}
