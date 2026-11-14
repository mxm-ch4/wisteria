package dev.wisteria.gate.modules;

import static dev.wisteria.gate.GameTestKit.chat;
import static dev.wisteria.gate.GameTestKit.check;
import static dev.wisteria.gate.GameTestKit.grabLastFrame;

import dev.wisteria.Client;
import dev.wisteria.gate.GameTestKit;
import dev.wisteria.gate.ModuleGameTest;
import dev.wisteria.modules.hud.Highway;
import dev.wisteria.modules.hud.HighwayLogic;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

/** The highway navigator with the real player: in the Nether by default, on an axis, off a diagonal, on a ring road. */
public final class HighwayGameTest extends ModuleGameTest {
	@Override
	protected void test(ClientGameTestContext context, TestSingleplayerContext world, Client client) {
		GameTestKit.hideProbe(true);
		Highway highway = (Highway) client.modules().get("highway");
		BlockPos feet = context.computeOnClient(minecraft -> minecraft.player.blockPosition());
		int y = feet.getY();
		world.getServer().runCommand("time set noon");
		chat(context, ".toggle highway on");
		context.waitTicks(5);
		check("highway: nothing in the Overworld while Nether Only is on", context.computeOnClient(minecraft -> highway.current()) == null);

		// In the Nether: on the +X axis, looking a little off it.
		world.getServer().runCommand("execute in minecraft:the_nether run tp @a 400.5 70 1.5 -80 0");
		world.getServer().runCommand("execute in minecraft:the_nether run fill 380 69 -3 420 69 5 minecraft:netherrack");
		world.getServer().runCommand("execute in minecraft:the_nether run fill 380 70 -3 420 73 5 minecraft:air");
		world.getServer().runCommand("effect give @a minecraft:night_vision 60 0 true");
		context.waitTicks(60);
		world.getConnection().waitForChunksRender();
		HighwayLogic.Nearest axis = context.computeOnClient(minecraft -> highway.current());
		check("highway: on the +X axis in the Nether (" + (axis == null ? null : axis.name()) + ")", axis != null && axis.name().equals("+X axis"));
		check("highway: it says so", context.computeOnClient(minecraft -> highway.guidance()).equals("on it"));
		grabLastFrame(context, client.platform(), "highway");

		// Back home; anywhere with Nether Only off.
		world.getServer().runCommand("execute in minecraft:overworld run tp @a " + (feet.getX() + 0.5) + " " + y + " " + (feet.getZ() + 0.5));
		context.waitTicks(40);
		chat(context, ".highway nether-only off");
		world.getServer().runCommand("tp @a 300.5 " + y + " 296.5 0 0");
		context.waitTicks(20);
		HighwayLogic.Nearest diagonal = context.computeOnClient(minecraft -> highway.current());
		check("highway: near the +X+Z diagonal, 3 blocks off", diagonal != null && diagonal.name().equals("+X+Z diagonal")
			&& Math.abs(diagonal.offset() - 4.0 / Math.sqrt(2.0)) < 0.2);
		check("highway: and which way to go (" + context.computeOnClient(minecraft -> highway.guidance()) + ")",
			context.computeOnClient(minecraft -> highway.guidance()).startsWith("3 off · go"));

		chat(context, ".highway ring-roads 1000");
		world.getServer().runCommand("tp @a 600.5 " + y + " 1001.5 0 0");
		context.waitTicks(20);
		HighwayLogic.Nearest ring = context.computeOnClient(minecraft -> highway.current());
		check("highway: a ring road from the menu", ring != null && ring.name().equals("ring 1000"));

		world.getServer().runCommand("tp @a " + (feet.getX() + 0.5) + " " + y + " " + (feet.getZ() + 0.5));
		world.getServer().runCommand("effect clear @a");
		context.waitTicks(20);
		chat(context, ".toggle highway off");
		chat(context, ".reset highway");
		GameTestKit.hideProbe(false);
	}
}
