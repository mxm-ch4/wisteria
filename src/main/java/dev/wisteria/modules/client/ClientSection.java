package dev.wisteria.modules.client;

import dev.wisteria.module.Category;
import dev.wisteria.module.Module;

/**
 * A group of client-wide controls in the ClickGUI's Client panel (profiles, friends, network…). Not something
 * that switches on and off: clicking its row only opens it. Every chat command has its control in one of these.
 */
abstract class ClientSection extends Module {
	ClientSection(String name, String description) {
		super(name, description, Category.CLIENT);
	}

	@Override
	protected boolean toggleable() {
		return false;
	}

	@Override
	protected boolean remembersEnabled() {
		return false;
	}
}
