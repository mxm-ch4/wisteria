package dev.wisteria.gate.modules;

import static dev.wisteria.gate.GameTestKit.chat;
import static dev.wisteria.gate.GameTestKit.check;
import static dev.wisteria.gate.GameTestKit.grabLastFrame;

import dev.wisteria.Client;
import dev.wisteria.gate.GameTestKit;
import dev.wisteria.gate.ModuleGameTest;
import dev.wisteria.modules.newchunks.NewChunkLogic;
import dev.wisteria.modules.newchunks.NewChunks;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

/**
 * NewChunks on real chunk and block packets. Old: water is poured in a chunk far away and left to flow while the
 * player cannot see it; when the player arrives, the chunk comes already holding flowing water. New: water poured in a
 * chunk just after it arrived starts flowing in front of the client.
 */
public final class NewChunksGameTest extends ModuleGameTest {
	@Override
	protected void test(ClientGameTestContext context, TestSingleplayerContext world, Client client) {
		GameTestKit.hideProbe(true);
		NewChunks module = (NewChunks) client.modules().get("newchunks");
		BlockPos feet = context.computeOnClient(minecraft -> minecraft.player.blockPosition());
		int y = feet.getY();
		// Far beyond the view distance, in chunk middles.
		int oldX = ((feet.getX() + 640) >> 4 << 4) + 8;
		int oldZ = (feet.getZ() >> 4 << 4) + 8;
		int newX = oldX + 16;
		int newZ = oldZ;
		world.getServer().runCommand("time set noon");

		chat(context, ".toggle newchunks on");
		chat(context, ".newchunks flow-window 600");
		check("newchunks: the far chunk is not loaded on the client", context.computeOnClient(minecraft -> minecraft.level.getChunkSource()
			.getChunk(oldX >> 4, oldZ >> 4, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) == null));

		// The old chunk: watered and flowing on the server while the client is away.
		world.getServer().runCommand("forceload add " + oldX + " " + oldZ);
		world.getServer().runCommand("setblock " + oldX + " " + y + " " + oldZ + " minecraft:water");
		context.waitTicks(60);
		world.getServer().runCommand("forceload remove " + oldX + " " + oldZ);

		// Go there: both chunks arrive now.
		// On the border between the two chunks, looking along it: the old one on the right, the new one on the left.
		world.getServer().runCommand("tp @a " + (oldX + 8.0) + " " + y + " " + (oldZ - 6.5) + " 0 30");
		context.waitTicks(80);
		world.getConnection().waitForChunksRender();
		check("newchunks: a chunk that arrives with flowing water is old",
			context.computeOnClient(minecraft -> module.stateOf(oldX >> 4, oldZ >> 4)) == NewChunkLogic.State.OLD);
		check("newchunks: a chunk without moving liquid stays unknown",
			context.computeOnClient(minecraft -> module.stateOf(newX >> 4, newZ >> 4)) == NewChunkLogic.State.UNKNOWN);

		// The new chunk: its water starts flowing in front of the client, soon after it arrived.
		world.getServer().runCommand("setblock " + newX + " " + y + " " + newZ + " minecraft:water");
		context.waitTicks(40);
		check("newchunks: liquid that starts flowing just after arrival makes it new",
			context.computeOnClient(minecraft -> module.stateOf(newX >> 4, newZ >> 4)) == NewChunkLogic.State.NEW);
		check("newchunks: an old chunk stays old when water moves in it later", context.computeOnClient(minecraft -> module.stateOf(oldX >> 4, oldZ >> 4)) == NewChunkLogic.State.OLD);

		chat(context, ".newchunks follow-height off");
		chat(context, ".newchunks height " + y);
		context.waitTicks(10);
		check("newchunks: plates drawn over the new and the old chunk (" + module.plates() + ")",
			context.computeOnClient(minecraft -> module.plates()) == 2);
		grabLastFrame(context, client.platform(), "newchunks");

		chat(context, ".newchunks show-old off");
		context.waitTicks(5);
		check("newchunks: Show Old off leaves only the new one", context.computeOnClient(minecraft -> module.plates()) == 1);

		chat(context, ".toggle newchunks off");
		chat(context, ".reset newchunks");
		world.getServer().runCommand("tp @a " + (feet.getX() + 0.5) + " " + feet.getY() + " " + (feet.getZ() + 0.5));
		context.waitTicks(40);
		GameTestKit.hideProbe(false);
	}
}
