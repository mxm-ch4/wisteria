package dev.wisteria.modules.storage;

import dev.wisteria.module.Category;
import dev.wisteria.module.Module;
import dev.wisteria.setting.BoolSetting;

/**
 * A shulker box's contents as a grid of items in its tooltip, wherever the game shows item tooltips (inventory,
 * chests, creative). Reads only the item's own data, which the client already has; nothing is sent.
 */
public final class ShulkerPreview extends Module {
	private final BoolSetting summary = this.add(new BoolSetting("Summary", "A line under the grid: stacks used and items in all", true));

	public ShulkerPreview() {
		super("Shulker Preview", "A grid of a shulker box's contents in its tooltip", Category.RENDER);
	}

	public boolean summary() {
		return this.summary.get();
	}
}
