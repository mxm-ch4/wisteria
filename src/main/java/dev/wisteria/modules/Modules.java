package dev.wisteria.modules;

import dev.wisteria.module.Module;
import dev.wisteria.modules.clickgui.ClickGui;
import dev.wisteria.modules.esp.EntityEsp;
import dev.wisteria.modules.fullbright.FullBright;
import dev.wisteria.modules.watermark.Watermark;
import java.util.List;

/**
 * Every module the client ships, in the order they appear in the ClickGUI. Adding a module is one line here;
 * see docs/module-api.md.
 */
public final class Modules {
	private Modules() {
	}

	public static List<Module> create() {
		return List.of(
			new FullBright(),
			new EntityEsp(),
			new Watermark(),
			new ClickGui()
		);
	}
}
