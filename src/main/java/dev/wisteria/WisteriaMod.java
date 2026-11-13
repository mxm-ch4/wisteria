package dev.wisteria;

import dev.wisteria.platform.game.GamePlatform;
import net.fabricmc.api.ClientModInitializer;

/** Fabric entrypoint: hooks the game, then starts the client. */
public final class WisteriaMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// For the network audit: the same game with the client's code loaded but doing nothing.
		if (Boolean.getBoolean("wisteria.disabled")) {
			return;
		}

		GamePlatform platform = new GamePlatform();
		platform.install();
		Wisteria.start(platform);
	}
}
