package dev.wisteria;

import dev.wisteria.platform.game.GamePlatform;
import net.fabricmc.api.ClientModInitializer;

/** Fabric entrypoint: hooks the game, then starts the client. */
public final class WisteriaMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		GamePlatform platform = new GamePlatform();
		platform.install();
		Wisteria.start(platform);
	}
}
