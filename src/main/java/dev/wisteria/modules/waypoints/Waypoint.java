package dev.wisteria.modules.waypoints;

/**
 * A named point on one server, in the dimension it was set in.
 *
 * @param dimension "minecraft:overworld", "minecraft:the_nether", …
 * @param group "" for none; "deaths" for the automatic death marks
 * @param colour ARGB
 */
public record Waypoint(String name, int x, int y, int z, String dimension, String group, int colour, long created) {
	public static final String OVERWORLD = "minecraft:overworld";
	public static final String NETHER = "minecraft:the_nether";
	public static final String DEATHS = "deaths";
}
