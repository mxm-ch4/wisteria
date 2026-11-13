package dev.wisteria.compat.v26_3;

import dev.wisteria.Wisteria;
import net.fabricmc.api.ClientModInitializer;

public final class Entrypoint implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		V26_3Platform platform = new V26_3Platform();
		platform.install();
		Wisteria.start(platform);
	}
}
