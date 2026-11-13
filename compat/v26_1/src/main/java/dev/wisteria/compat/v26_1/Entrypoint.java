package dev.wisteria.compat.v26_1;

import dev.wisteria.Wisteria;
import net.fabricmc.api.ClientModInitializer;

public final class Entrypoint implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		V26_1Platform platform = new V26_1Platform();
		platform.install();
		Wisteria.start(platform);
	}
}
