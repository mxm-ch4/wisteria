package dev.wisteria.gate.modules;

import static dev.wisteria.gate.GameTestKit.chat;
import static dev.wisteria.gate.GameTestKit.check;
import static dev.wisteria.gate.GameTestKit.grabLastFrame;

import dev.wisteria.Client;
import dev.wisteria.gate.GameTestKit.Frame;
import dev.wisteria.gate.ModuleGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** The worked example of a module test: switch it on in a real world and look at the frame. */
public final class FullBrightGameTest extends ModuleGameTest {
	@Override
	protected void test(ClientGameTestContext context, TestSingleplayerContext world, Client client) {
		world.getServer().runCommand("time set midnight");
		context.waitTicks(40);
		Frame night = grabLastFrame(context, client.platform(), "fullbright-off");

		chat(context, ".toggle fullbright on");
		context.waitTicks(20);
		check("fullbright: switched on", context.computeOnClient(minecraft -> client.modules().get("fullbright").isEnabled()));
		Frame lit = grabLastFrame(context, client.platform(), "fullbright-on");

		// Ground to the right of the crosshair: clear of the chat on the left and of the hand below.
		int dark = night.meanLuminance(night.width() * 6 / 10, night.height() * 52 / 100, night.width() * 35 / 100, night.height() * 16 / 100);
		int bright = lit.meanLuminance(lit.width() * 6 / 10, lit.height() * 52 / 100, lit.width() * 35 / 100, lit.height() * 16 / 100);
		check("fullbright: ground at midnight goes from " + dark + " to " + bright, bright > dark * 3 / 2 && bright > dark + 15);

		// Settings apply while it runs.
		chat(context, ".fullbright brightness 1");
		context.waitTicks(10);
		Frame dim = grabLastFrame(context, client.platform(), "fullbright-dim");
		int dimmed = dim.meanLuminance(dim.width() * 6 / 10, dim.height() * 52 / 100, dim.width() * 35 / 100, dim.height() * 16 / 100);
		check("fullbright: lower brightness setting gives " + dimmed, dimmed < bright - 10);

		chat(context, ".toggle fullbright off");
		chat(context, ".reset fullbright");
		check("fullbright: switched off", !context.computeOnClient(minecraft -> client.modules().get("fullbright").isEnabled()));
	}
}
