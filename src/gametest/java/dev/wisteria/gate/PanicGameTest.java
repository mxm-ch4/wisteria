package dev.wisteria.gate;

import static dev.wisteria.gate.GameTestKit.chat;
import static dev.wisteria.gate.GameTestKit.check;
import static dev.wisteria.gate.GameTestKit.grabLastFrame;

import com.mojang.blaze3d.platform.InputConstants;
import dev.wisteria.Client;
import dev.wisteria.gate.GameTestKit.Frame;
import dev.wisteria.media.FakeMediaSource;
import dev.wisteria.media.MediaSource;
import dev.wisteria.module.Module;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

/**
 * The panic button in a real world: ESP, HUD, Xray Predicted, BaseFinder and the media widget on, a toast and client
 * lines in chat; one key press later the frame shows the game alone, and Restore brings the same modules back.
 */
public final class PanicGameTest extends ModuleGameTest {
	private static final List<String> SHOWN = List.of("esp", "watermark", "coordinates", "fps", "notifications", "xraypredicted", "basefinder",
		"media");

	@Override
	protected void test(ClientGameTestContext context, TestSingleplayerContext world, Client client) {
		GameTestKit.hideProbe(true);
		BlockPos feet = context.computeOnClient(minecraft -> minecraft.player.blockPosition());
		world.getServer().runCommand("time set noon");
		world.getServer().runCommand("tp @a " + (feet.getX() + 0.5) + " " + feet.getY() + " " + (feet.getZ() + 0.5) + " 0 30");
		world.getServer().runCommand("summon minecraft:husk " + (feet.getX() + 0.5) + " " + feet.getY() + " " + (feet.getZ() + 4.5)
			+ " {NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[180f,0f]}");
		FakeMediaSource fake = new FakeMediaSource().add("Panic Test Track", "Gate", 200);
		MediaSource real = context.computeOnClient(minecraft -> client.media());
		context.runOnClient(minecraft -> client.useMediaSource(fake));

		// What was on before (other tests leave Coordinates and Watermark on for the restart check).
		List<String> before = context.computeOnClient(minecraft -> client.modules().all().stream()
			.filter(module -> module.isToggleable() && module.isEnabled()).map(Module::id).toList());
		context.runOnClient(minecraft -> client.modules().all().forEach(module -> {
			if (module.isToggleable() && module.isEnabled()) {
				client.setEnabled(module, false);
			}
		}));
		context.runOnClient(minecraft -> minecraft.gui.hud.getChat().clearMessages(false));
		context.waitTicks(30);
		Frame plain = grabLastFrame(context, client.platform(), "panic-plain");

		context.runOnClient(minecraft -> SHOWN.forEach(id -> client.setEnabled(client.modules().get(id), true)));
		chat(context, ".privacy panic-key f9");
		context.runOnClient(minecraft -> client.platform().chat().print("a client line that must go"));
		context.waitTicks(30);
		Frame shown = grabLastFrame(context, client.platform(), "panic-before");
		List<dev.wisteria.hud.HudManager.Drawn> drawn = context.computeOnClient(minecraft -> client.hud().lastDrawn());
		float scale = GameTestKit.guiScale(context);
		float shownDiff = difference(plain, shown, drawn, scale);
		check("panic: with the client on, " + drawn.size() + " HUD elements change the frame (" + percent(shownDiff) + ")",
			drawn.size() >= 4 && shownDiff > 0.2F);

		context.getInput().pressKey(InputConstants.KEY_F9);
		context.waitTick();
		List<String> stillOn = context.computeOnClient(minecraft -> client.modules().all().stream()
			.filter(module -> module.isToggleable() && module.isEnabled()).map(Module::id).toList());
		check("panic: one tick later every module is off (" + stillOn + ")", stillOn.isEmpty());
		check("panic: the media source is stopped", !fake.started());
		context.waitTicks(5);
		check("panic: no HUD element is drawn", context.computeOnClient(minecraft -> client.hud().lastDrawn().isEmpty()));
		Frame hidden = grabLastFrame(context, client.platform(), "panic");
		float hiddenDiff = difference(plain, hidden, drawn, scale);
		check("panic: where the HUD was, the frame is the game alone (" + percent(hiddenDiff) + " differs from the plain frame)", hiddenDiff < 0.05F);
		int lines = context.computeOnClient(minecraft -> ((dev.wisteria.mixin.ChatComponentAccessor) minecraft.gui.hud.getChat()).wisteria$allMessages().stream()
			.filter(message -> message.content().getString().startsWith("Wisteria » ")).toList().size());
		check("panic: the client's lines are gone from chat (" + lines + ")", lines == 0);
		context.runOnClient(minecraft -> client.platform().chat().print("printed during the panic"));
		context.runOnClient(minecraft -> client.hud().notify("t", "during the panic", dev.wisteria.hud.Toasts.Kind.INFO));
		context.waitTicks(3);
		int printed = context.computeOnClient(minecraft -> ((dev.wisteria.mixin.ChatComponentAccessor) minecraft.gui.hud.getChat()).wisteria$allMessages().size());
		check("panic: nothing is printed or toasted while it is on", printed == 0 && context.computeOnClient(minecraft -> client.hud().toasts().shown().isEmpty()));
		context.runOnClient(minecraft -> client.modules().get("fullbright").bind().parse("g"));
		context.getInput().pressKey(InputConstants.KEY_G);
		context.waitTick();
		check("panic: a module bind does nothing", !context.computeOnClient(minecraft -> client.modules().get("fullbright").isEnabled()));

		// The key again: Restore.
		context.getInput().pressKey(InputConstants.KEY_F9);
		context.waitTick();
		List<String> back = context.computeOnClient(minecraft -> new ArrayList<>(client.modules().all().stream()
			.filter(module -> module.isToggleable() && module.isEnabled()).map(Module::id).toList()));
		check("panic: Restore brings back the same modules (" + back + ")", back.containsAll(SHOWN) && SHOWN.containsAll(back));
		check("panic: the media source runs again", fake.started());
		context.waitTicks(20);
		check("panic: the HUD is back", context.computeOnClient(minecraft -> !client.hud().lastDrawn().isEmpty()));

		// Back to how the other tests left things.
		context.runOnClient(minecraft -> {
			client.modules().get("fullbright").bind().reset();
			client.modules().all().forEach(module -> {
				if (module.isToggleable() && module.isEnabled() != before.contains(module.id())) {
					client.setEnabled(module, before.contains(module.id()));
				}
			});
			client.modules().get("privacy").setting("panic-key").reset();
			client.useMediaSource(real);
		});
		chat(context, ".reset xraypredicted");
		chat(context, ".reset basefinder");
		GameTestKit.hideProbe(false);
	}

	/** Share of pixels inside the HUD elements' boxes that differ clearly between two frames. */
	private static float difference(Frame a, Frame b, List<dev.wisteria.hud.HudManager.Drawn> drawn, float scale) {
		int differing = 0;
		int total = 0;
		for (dev.wisteria.hud.HudManager.Drawn item : drawn) {
			int x0 = (int) (item.rect().x() * scale);
			int y0 = (int) (item.rect().y() * scale);
			int x1 = Math.min(a.width(), (int) ((item.rect().x() + item.rect().width()) * scale));
			int y1 = Math.min(a.height(), (int) ((item.rect().y() + item.rect().height()) * scale));
			for (int y = Math.max(0, y0); y < y1; y += 2) {
				for (int x = Math.max(0, x0); x < x1; x += 2) {
					total++;
					differing += GameTestKit.distance(a.pixels()[y * a.width() + x], b.pixels()[y * b.width() + x]) > 48 ? 1 : 0;
				}
			}
		}

		return total == 0 ? 0.0F : differing / (float) total;
	}

	private static String percent(float share) {
		return String.format(java.util.Locale.ROOT, "%.2f%%", share * 100.0F);
	}
}
