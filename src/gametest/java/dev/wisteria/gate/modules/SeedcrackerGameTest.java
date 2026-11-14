package dev.wisteria.gate.modules;

import static dev.wisteria.gate.GameTestKit.check;

import dev.wisteria.Client;
import dev.wisteria.gate.ModuleGameTest;
import dev.wisteria.modules.client.SeedsSection;
import dev.wisteria.setting.ActionSetting;
import dev.wisteria.setting.PasswordSetting;
import java.util.List;
import kaptainwutax.seedcrackerX.api.SeedCrackerAPI;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * SeedcrackerX integration without SeedcrackerX: the test looks up the "seedcrackerx" entrypoints exactly as the mod
 * does and calls pushWorldSeed from another thread, as its cracker would. The seeds are made up.
 */
public final class SeedcrackerGameTest extends ModuleGameTest {
	@Override
	protected void test(ClientGameTestContext context, TestSingleplayerContext world, Client client) {
		SeedsSection seeds = client.modules().get(SeedsSection.class);
		PasswordSetting field = (PasswordSetting) seeds.setting("seed");
		context.runOnClient(minecraft -> field.store(""));

		List<SeedCrackerAPI> entrypoints = FabricLoader.getInstance().getEntrypoints("seedcrackerx", SeedCrackerAPI.class);
		check("seedcracker: our entrypoint is registered (" + entrypoints.size() + ")", entrypoints.size() == 1);
		check("seedcracker: SeedcrackerX itself is not required", !FabricLoader.getInstance().isModLoaded("seedcrackerx"));

		this.push(context, entrypoints, 111_111_111L);
		check("seedcracker: a cracked seed is saved for this server", this.current(context, seeds) == 111_111_111L);
		check("seedcracker: marked as from SeedcrackerX", SeedsSection.FROM_SEEDCRACKERX.equals(context.computeOnClient(minecraft -> seeds.currentSource())));

		this.push(context, entrypoints, 222_222_222L);
		check("seedcracker: a newer crack replaces the older one", this.current(context, seeds) == 222_222_222L);

		context.runOnClient(minecraft -> field.store("333333333"));
		check("seedcracker: typing a seed makes it the player's own", "".equals(context.computeOnClient(minecraft -> seeds.currentSource())));
		this.push(context, entrypoints, 444_444_444L);
		check("seedcracker: the player's seed is not overwritten", this.current(context, seeds) == 333_333_333L);
		check("seedcracker: the different seed waits in the menu",
			context.computeOnClient(minecraft -> seeds.pendingOffer().orElse(0L)) == 444_444_444L);
		context.runOnClient(minecraft -> ((ActionSetting) seeds.setting("use-offered-seed")).press());
		check("seedcracker: Use Offered Seed takes it", this.current(context, seeds) == 444_444_444L
			&& context.computeOnClient(minecraft -> seeds.pendingOffer().isEmpty()));

		context.runOnClient(minecraft -> field.store(""));
		check("seedcracker: Forget leaves nothing", context.computeOnClient(minecraft -> seeds.current().isEmpty()));
	}

	private void push(ClientGameTestContext context, List<SeedCrackerAPI> entrypoints, long seed) {
		Thread cracker = new Thread(() -> entrypoints.forEach(entrypoint -> entrypoint.pushWorldSeed(seed)), "fake SeedcrackerX");
		cracker.start();
		try {
			cracker.join(5_000L);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}

		context.waitTicks(5);
	}

	private long current(ClientGameTestContext context, SeedsSection seeds) {
		return context.computeOnClient(minecraft -> seeds.current().orElse(0L));
	}
}
