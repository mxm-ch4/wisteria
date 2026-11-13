package dev.wisteria.gate;

import dev.wisteria.Client;
import dev.wisteria.Wisteria;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Base for a module's in-game test: you get a fresh world and the running client. Register the subclass in
 * src/gametest/resources/fabric.mod.json under "fabric-client-gametest". See docs/module-api.md.
 */
public abstract class ModuleGameTest implements FabricClientGameTest {
	@Override
	public final void runTest(ClientGameTestContext context) {
		// The second launch of tools/gate.sh only checks persistence; module tests run in the first.
		if (GameTestKit.isRestartPhase()) {
			return;
		}

		try (TestSingleplayerContext world = GameTestKit.newWorld(context)) {
			this.test(context, world, Wisteria.client());
		}
	}

	/**
	 * @param world the server side: run commands with {@code world.getServer().runCommand("...")}
	 */
	protected abstract void test(ClientGameTestContext context, TestSingleplayerContext world, Client client);
}
