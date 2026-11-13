package dev.wisteria.gate;

import static dev.wisteria.gate.GameTestKit.check;
import static dev.wisteria.gate.GameTestKit.grabLastFrame;

import com.mojang.blaze3d.platform.InputConstants;
import dev.wisteria.Client;
import dev.wisteria.Wisteria;
import dev.wisteria.gate.GameTestKit.Frame;
import dev.wisteria.platform.game.BrandedTitleScreen;
import dev.wisteria.ui.MainMenuScreen;
import dev.wisteria.ui.gui.Controls;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The branded main menu: it takes the title screen's place, its buttons open the game's screens with the real mouse
 * and the keyboard, Escape comes back to it, and switching it off brings the game's own back.
 */
public final class MenuGameTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("WisteriaGate");

	@Override
	public void runTest(ClientGameTestContext context) {
		if (GameTestKit.isRestartPhase()) {
			context.setScreen(TitleScreen::new);
			context.waitTicks(5);
			check("menu restart: the branded menu after a restart", context.computeOnClient(minecraft -> GateHooks.currentScreen(minecraft) instanceof BrandedTitleScreen));
			return;
		}

		Client client = Wisteria.client();
		GameTestKit.hideProbe(true);
		try {
			context.setScreen(TitleScreen::new);
			context.waitTicks(20);
			check("menu: the title screen is the branded menu", context.computeOnClient(minecraft -> GateHooks.currentScreen(minecraft) instanceof BrandedTitleScreen));
			Frame frame = grabLastFrame(context, client.platform(), "menu");
			// In whatever theme is on (earlier gate tests switch it): pixels near its accent and gradient colours.
			int[] brand = context.computeOnClient(minecraft -> new int[] {client.themes().current().accent(), client.themes().current().gradientStart(),
				client.themes().current().gradientEnd()});
			int mark = frame.count(0, 0, frame.width() / 2, frame.height(), argb -> GameTestKit.distance(argb, brand[0]) < 60
				|| GameTestKit.distance(argb, brand[1]) < 60 || GameTestKit.distance(argb, brand[2]) < 60);
			LOG.info("menu: {} accent pixels in the left half", mark);
			check("menu: the mark and the buttons are drawn", mark > 400);

			float scale = GameTestKit.guiScale(context);
			Controls.Control multiplayer = this.control(context, "menu:multiplayer");
			GameTestKit.click(context, multiplayer.centerX(), multiplayer.centerY(), InputConstants.MOUSE_BUTTON_LEFT, scale);
			context.waitTicks(5);
			Object opened = context.computeOnClient(GateHooks::currentScreen);
			LOG.info("menu: Multiplayer opened {}", opened == null ? "nothing" : opened.getClass().getSimpleName());
			check("menu: Multiplayer opens the game's server list", opened instanceof JoinMultiplayerScreen || opened instanceof SafetyScreen);
			grabLastFrame(context, client.platform(), "menu-multiplayer");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(5);
			check("menu: Escape comes back to the branded menu", context.computeOnClient(minecraft -> GateHooks.currentScreen(minecraft) instanceof BrandedTitleScreen));

			// Keyboard: down once to Singleplayer, Enter.
			context.getInput().pressKey(InputConstants.KEY_DOWN);
			context.waitTicks(2);
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(5);
			// With no saved worlds the game goes straight on to creating one.
			check("menu: Down and Enter open Singleplayer", context.computeOnClient(minecraft -> GateHooks.currentScreen(minecraft) instanceof SelectWorldScreen
				|| GateHooks.currentScreen(minecraft) instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen));
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(5);

			context.runOnClient(minecraft -> client.modules().get("interface").setting("branded-menu").parse("off"));
			context.setScreen(TitleScreen::new);
			context.waitTicks(5);
			check("menu: switched off, the game's own title screen is back",
				context.computeOnClient(minecraft -> {
					Object screen = GateHooks.currentScreen(minecraft);
					return screen != null && screen.getClass() == TitleScreen.class;
				}));
		} finally {
			context.runOnClient(minecraft -> client.modules().get("interface").setting("branded-menu").parse("on"));
			context.setScreen(TitleScreen::new);
			context.waitTicks(5);
			GameTestKit.hideProbe(false);
		}
	}

	private Controls.Control control(ClientGameTestContext context, String id) {
		return context.computeOnClient(minecraft -> {
			MainMenuScreen menu = (MainMenuScreen)((BrandedTitleScreen)GateHooks.currentScreen(minecraft)).menu();
			return menu.controls().snapshot().stream().filter(control -> control.id().equals(id)).findFirst().orElseThrow();
		});
	}
}
