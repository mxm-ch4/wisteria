package dev.wisteria.modules;

import dev.wisteria.module.Module;
import dev.wisteria.modules.clickgui.ClickGui;
import dev.wisteria.modules.esp.EntityEsp;
import dev.wisteria.modules.fullbright.FullBright;
import dev.wisteria.modules.hud.ArrayListHud;
import dev.wisteria.modules.hud.Coordinates;
import dev.wisteria.modules.hud.Direction;
import dev.wisteria.modules.hud.Fps;
import dev.wisteria.modules.hud.HudEditor;
import dev.wisteria.modules.hud.Keybinds;
import dev.wisteria.modules.hud.Notifications;
import dev.wisteria.modules.hud.Potions;
import dev.wisteria.modules.hud.TargetHud;
import dev.wisteria.modules.hud.Tps;
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
			new dev.wisteria.modules.xray.XrayPredicted(),
			new ClickGui(),
			// HUD
			new Watermark(),
			new ArrayListHud(),
			new Coordinates(),
			new Direction(),
			new Fps(),
			new Tps(),
			new TargetHud(),
			new Potions(),
			new Notifications(),
			new Keybinds(),
			new dev.wisteria.modules.hud.ProxyHud(),
			new HudEditor(),
			// Client: every command's counterpart in the menu
			new dev.wisteria.modules.client.ProxySection(),
			new dev.wisteria.modules.client.ProfilesSection(),
			new dev.wisteria.modules.client.FriendsSection(),
			new dev.wisteria.modules.client.NetworkSection(),
			new dev.wisteria.modules.client.ChatSection(),
			new dev.wisteria.modules.client.PrivacySection(),
			new dev.wisteria.modules.client.SeedsSection(),
			new dev.wisteria.modules.client.InterfaceSection(),
			new dev.wisteria.modules.client.ToolsSection()
		);
	}
}
