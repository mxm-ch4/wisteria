package dev.wisteria.gate.modules;

import static dev.wisteria.gate.GameTestKit.chat;
import static dev.wisteria.gate.GameTestKit.check;
import static dev.wisteria.gate.GameTestKit.grabLastFrame;

import com.mojang.blaze3d.platform.InputConstants;
import dev.wisteria.Client;
import dev.wisteria.gate.GameTestKit;
import dev.wisteria.gate.GameTestKit.Frame;
import dev.wisteria.gate.ModuleGameTest;
import dev.wisteria.hud.HudManager;
import dev.wisteria.media.FakeMediaSource;
import dev.wisteria.media.MediaSource;
import dev.wisteria.media.WinRtMediaSource;
import dev.wisteria.setting.ActionSetting;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * The media widget on a fake source (no Windows and no music here): it shows the track, the menu buttons and its own
 * keys reach the player, pause with Hide When Paused hides it. The real Windows source is checked only for saying it
 * needs Windows: the live check with music is the owner's.
 */
public final class MediaGameTest extends ModuleGameTest {
	@Override
	protected void test(ClientGameTestContext context, TestSingleplayerContext world, Client client) {
		GameTestKit.hideProbe(true);
		FakeMediaSource fake = new FakeMediaSource().add("Wisteria Gate Track", "The Test Band", 215).add("Second Track", "Another Band", 180);
		fake.seek(83_000L);
		MediaSource real = context.computeOnClient(minecraft -> client.media());
		context.runOnClient(minecraft -> client.useMediaSource(fake));
		world.getServer().runCommand("time set noon");
		context.waitTicks(10);
		Frame off = grabLastFrame(context, client.platform(), "media-off");

		chat(context, ".toggle media on");
		context.waitTicks(20);
		check("media: switching on starts the source", fake.started());
		HudManager.Drawn drawn = this.drawn(context, client);
		check("media: the widget is drawn with the track", drawn != null);
		Frame on = grabLastFrame(context, client.platform(), "media");
		float scale = GameTestKit.guiScale(context);
		int changed = 0;
		int total = 0;
		for (int y = (int) (drawn.rect().y() * scale); y < (int) ((drawn.rect().y() + drawn.rect().height()) * scale); y += 2) {
			for (int x = (int) (drawn.rect().x() * scale); x < (int) ((drawn.rect().x() + drawn.rect().width()) * scale); x += 2) {
				total++;
				changed += GameTestKit.distance(off.at(x, y), on.at(x, y)) > 30 ? 1 : 0;
			}
		}

		check("media: visible in the frame (" + changed + " of " + total + " pixels changed)", changed > total / 5);

		// Menu buttons.
		context.runOnClient(minecraft -> ((ActionSetting) client.modules().get("media").setting("next")).press());
		check("media: Next in the menu skips the track", fake.received().contains(MediaSource.Command.NEXT)
			&& fake.current().orElseThrow().title().equals("Second Track"));
		context.runOnClient(minecraft -> ((ActionSetting) client.modules().get("media").setting("previous")).press());
		check("media: Previous goes back", fake.current().orElseThrow().title().equals("Wisteria Gate Track"));

		// Its own key, pressed for real.
		chat(context, ".media play-pause-key k");
		context.getInput().pressKey(InputConstants.KEY_K);
		context.waitTicks(5);
		check("media: the Play Pause key pauses", !fake.current().orElseThrow().playing());

		chat(context, ".media hide-when-paused on");
		context.waitTicks(20);
		check("media: paused with Hide When Paused, it is gone", this.drawn(context, client) == null);
		context.getInput().pressKey(InputConstants.KEY_K);
		context.waitTicks(20);
		check("media: playing again, it is back", this.drawn(context, client) != null);

		// The real source here: Linux, so it says what it needs instead of starting anything.
		WinRtMediaSource smtc = new WinRtMediaSource();
		smtc.start();
		check("media: the Windows source says it needs Windows (" + smtc.status() + ")", WinRtMediaSource.available() || smtc.status().contains("Windows"));
		smtc.stop();
		// It calls Windows in-process through the foreign function API; with native access on (as the launcher starts
		// the game) Java prints no warning about it.
		check("media: native access is on for the client, so no warning is printed",
			WinRtMediaSource.class.getModule().isNativeAccessEnabled());
		boolean children = ProcessHandle.current().children().findAny().isPresent();
		check("media: the game has no child processes", !children);

		chat(context, ".toggle media off");
		check("media: switching off stops the source", !fake.started());
		chat(context, ".reset media");
		context.runOnClient(minecraft -> client.useMediaSource(real));
		GameTestKit.hideProbe(false);
	}

	private HudManager.Drawn drawn(ClientGameTestContext context, Client client) {
		List<HudManager.Drawn> all = context.computeOnClient(minecraft -> client.hud().lastDrawn());
		return all.stream().filter(item -> item.element().id().equals("media")).findFirst().orElse(null);
	}
}
