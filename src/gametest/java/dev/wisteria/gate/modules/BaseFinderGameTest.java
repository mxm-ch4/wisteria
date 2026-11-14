package dev.wisteria.gate.modules;

import static dev.wisteria.gate.GameTestKit.chat;
import static dev.wisteria.gate.GameTestKit.check;
import static dev.wisteria.gate.GameTestKit.grabLastFrame;

import dev.wisteria.Client;
import dev.wisteria.gate.GameTestKit;
import dev.wisteria.gate.ModuleGameTest;
import dev.wisteria.modules.basefinder.BaseFinder;
import dev.wisteria.modules.basefinder.BaseLogic;
import dev.wisteria.setting.ListSetting;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

/**
 * BaseFinder in a real world: a small base and a big one built by the server, a lone spawner (a dungeon) and
 * a village-like hut. The bases are listed and rated, the others are not; the scan keeps to its time budget; the list
 * sorts both ways, survives switching off and on, and loses an entry removed from the menu.
 */
public final class BaseFinderGameTest extends ModuleGameTest {
	@Override
	protected void test(ClientGameTestContext context, TestSingleplayerContext world, Client client) {
		GameTestKit.hideProbe(true);
		BaseFinder finder = (BaseFinder) client.modules().get("basefinder");
		BlockPos feet = context.computeOnClient(minecraft -> minecraft.player.blockPosition());
		int x = feet.getX();
		int y = feet.getY();
		int z = feet.getZ();
		world.getServer().runCommand("time set noon");

		// Built before switching on, so the first scan finds them.
		int bigX = align(x + 40);
		int bigZ = align(z + 8);
		world.getServer().runCommand("fill " + bigX + " " + y + " " + bigZ + " " + (bigX + 7) + " " + (y + 3) + " " + (bigZ + 7) + " minecraft:white_concrete hollow");
		world.getServer().runCommand("fill " + (bigX + 1) + " " + y + " " + (bigZ + 1) + " " + (bigX + 6) + " " + y + " " + (bigZ + 2) + " minecraft:chest");
		world.getServer().runCommand("fill " + (bigX + 1) + " " + (y + 1) + " " + (bigZ + 1) + " " + (bigX + 6) + " " + (y + 1) + " " + (bigZ + 1) + " minecraft:hopper");
		world.getServer().runCommand("setblock " + (bigX + 3) + " " + y + " " + (bigZ + 5) + " minecraft:enchanting_table");
		world.getServer().runCommand("setblock " + (bigX + 4) + " " + y + " " + (bigZ + 5) + " minecraft:observer");
		int smallX = align(x - 12);
		int smallZ = align(z - 12);
		world.getServer().runCommand("fill " + smallX + " " + y + " " + smallZ + " " + (smallX + 3) + " " + y + " " + smallZ + " minecraft:barrel");
		world.getServer().runCommand("setblock " + smallX + " " + y + " " + (smallZ + 2) + " minecraft:piston");
		world.getServer().runCommand("setblock " + (smallX + 2) + " " + y + " " + (smallZ + 2) + " minecraft:anvil");
		int dungeonX = align(x + 8);
		int dungeonZ = align(z - 40);
		world.getServer().runCommand("setblock " + dungeonX + " " + y + " " + dungeonZ + " minecraft:spawner");
		int hutX = align(x - 40);
		int hutZ = align(z + 24);
		world.getServer().runCommand("setblock " + hutX + " " + y + " " + hutZ + " minecraft:chest");
		world.getServer().runCommand("setblock " + (hutX + 2) + " " + y + " " + hutZ + " minecraft:red_bed");
		world.getServer().runCommand("setblock " + (hutX + 4) + " " + y + " " + hutZ + " minecraft:glass_pane");
		context.waitTicks(20);

		chat(context, ".toggle basefinder on");
		double worst = 0.0;
		for (int i = 0; i < 200 && (this.found(context, finder).size() < 2 || context.computeOnClient(minecraft -> finder.pendingCount()) > 0); i++) {
			context.waitTick();
			worst = Math.max(worst, context.computeOnClient(minecraft -> finder.lastFrameMillis()));
		}

		List<BaseLogic.Entry> found = this.found(context, finder);
		check("basefinder: both bases listed (" + found.size() + ": " + found + ")", this.has(found, bigX, bigZ) && this.has(found, smallX, smallZ));
		check("basefinder: the dungeon spawner is not", !this.has(found, dungeonX, dungeonZ));
		check("basefinder: the hut with a chest and a bed is not", !this.has(found, hutX, hutZ));
		BaseLogic.Entry big = this.at(found, bigX, bigZ);
		BaseLogic.Entry small = this.at(found, smallX, smallZ);
		check("basefinder: the big base rates higher (" + big.score() + " > " + small.score() + ")", big.score() > small.score());
		double budget = context.computeOnClient(minecraft -> Double.parseDouble(finder.setting("budget").format()));
		check("basefinder: scanning stays within its budget per frame (worst " + worst + " ms of " + budget + ")", worst <= budget + 2.0);
		check("basefinder: chunks scan fast (" + finder.millisPerChunk() + " ms each)", finder.millisPerChunk() < 5.0);

		// Sorting, both ways.
		check("basefinder: by score, the big one first", this.found(context, finder).get(0) == big || this.found(context, finder).get(0).equals(big));
		chat(context, ".basefinder sort distance");
		check("basefinder: by distance, the small one (nearer) first", this.found(context, finder).get(0).equals(small));
		chat(context, ".basefinder sort score");

		// A base built while watching is found too.
		int newX = align(x + 8);
		int newZ = align(z + 40);
		world.getServer().runCommand("fill " + newX + " " + y + " " + newZ + " " + (newX + 5) + " " + y + " " + newZ + " minecraft:barrel");
		world.getServer().runCommand("setblock " + newX + " " + (y + 1) + " " + newZ + " minecraft:beacon");
		context.waitTicks(20);
		check("basefinder: a base built in view is found", this.has(this.found(context, finder), newX, newZ));

		// The world view: boxes and beams.
		world.getServer().runCommand("tp @a " + (x + 0.5) + " " + y + " " + (z + 0.5) + " -70 4");
		context.waitTicks(20);
		grabLastFrame(context, client.platform(), "basefinder");

		// Saved per server in the profile: off and on brings them back.
		chat(context, ".toggle basefinder off");
		chat(context, ".toggle basefinder on");
		context.waitTicks(2);
		check("basefinder: the list survives switching off and on", this.has(this.found(context, finder), bigX, bigZ));

		// Removing from the menu.
		ListSetting list = (ListSetting) finder.setting("found");
		String line = context.computeOnClient(minecraft -> list.entries().stream()
			.filter(entry -> entry.contains(" " + this.at(this.found(context, finder), smallX, smallZ).x() + " ")).findFirst().orElseThrow());
		context.runOnClient(minecraft -> list.remove(line));
		check("basefinder: an entry removed in the menu is gone", !this.has(this.found(context, finder), smallX, smallZ));

		chat(context, ".basefinder clear");
		chat(context, ".toggle basefinder off");
		chat(context, ".reset basefinder");
		GameTestKit.hideProbe(false);
	}

	/** Two blocks into the chunk, so an eight-block build stays inside one chunk. */
	private static int align(int block) {
		return (block >> 4 << 4) + 2;
	}

	private List<BaseLogic.Entry> found(ClientGameTestContext context, BaseFinder finder) {
		return context.computeOnClient(minecraft -> finder.found());
	}

	private boolean has(List<BaseLogic.Entry> found, int blockX, int blockZ) {
		return this.at(found, blockX, blockZ) != null;
	}

	private BaseLogic.Entry at(List<BaseLogic.Entry> found, int blockX, int blockZ) {
		return found.stream().filter(entry -> entry.chunkX() == blockX >> 4 && entry.chunkZ() == blockZ >> 4).findFirst().orElse(null);
	}
}
