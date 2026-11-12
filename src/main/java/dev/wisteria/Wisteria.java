package dev.wisteria;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Wisteria implements ClientModInitializer {
	public static final String ID = "wisteria";
	public static final String NAME = "Wisteria";
	public static final Logger LOG = LoggerFactory.getLogger(NAME);

	@Override
	public void onInitializeClient() {
		LOG.info("{} loaded", NAME);
	}
}
