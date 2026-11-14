package dev.wisteria.modules.client;

import dev.wisteria.Client;
import dev.wisteria.Wisteria;
import kaptainwutax.seedcrackerX.api.SeedCrackerAPI;
import net.minecraft.client.Minecraft;

/**
 * Registered as the {@code seedcrackerx} entrypoint: when SeedcrackerX is installed and cracks the world seed, it hands
 * it here, and it goes to Client → Seeds for the server the player is on. Without SeedcrackerX nothing calls this.
 * Nothing is sent anywhere: the seed only goes into the profile.
 */
public final class SeedcrackerBridge implements SeedCrackerAPI {
	@Override
	public void pushWorldSeed(long seed) {
		// SeedcrackerX calls from its own cracking thread.
		Minecraft.getInstance().execute(() -> {
			Client client = Wisteria.clientOrNull();
			SeedsSection seeds = client == null ? null : client.modules().get(SeedsSection.class);
			if (seeds != null) {
				seeds.offer(seed, SeedsSection.FROM_SEEDCRACKERX);
			}
		});
	}
}
