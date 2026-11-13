package dev.wisteria.compat.v26_2;

import dev.wisteria.Wisteria;
import net.fabricmc.api.ClientModInitializer;

public final class Entrypoint implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		V26_2Platform platform = new V26_2Platform();
		platform.install();
		Wisteria.start(platform);
	}
}
