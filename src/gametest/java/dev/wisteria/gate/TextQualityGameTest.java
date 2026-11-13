package dev.wisteria.gate;

import static dev.wisteria.gate.GameTestKit.check;
import static dev.wisteria.gate.GameTestKit.grabLastFrame;

import dev.wisteria.Client;
import dev.wisteria.Wisteria;
import dev.wisteria.ui.DesignScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Text at 1920x1080 with GUI scale 2, 3 and 4 on the design reference screen. Saves the frames for a look
 * and fails if the text has hard pixel steps (see GameTestKit.Frame#edgeSoftness).
 */
public final class TextQualityGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (GameTestKit.isAuditPhase()) {
			return;
		}

		if (GameTestKit.isRestartPhase()) {
			return;
		}

		Client client = Wisteria.client();
		context.getInput().resizeWindow(1920, 1080);
		context.waitTicks(5);
		int originalScale = context.computeOnClient(minecraft -> minecraft.options.guiScale().get());
		for (int scale : new int[] {2, 3, 4}) {
			context.runOnClient(minecraft -> {
				minecraft.options.guiScale().set(scale);
				minecraft.resizeGui();
			});
			context.runOnClient(minecraft -> client.platform().screens().open(new DesignScreen(client)));
			context.waitTicks(20);
			GameTestKit.Frame frame = grabLastFrame(context, client.platform(), "text-scale-" + scale);
			check("text: frame is 1920x1080 at GUI scale " + scale, frame.width() == 1920 && frame.height() == 1080);
			// The type column of the design screen: panel 408 x 222 GUI units, centred; column at (148, 54), 122 x 66.
			int panelX = (1920 - 408 * scale) / 2;
			int panelY = (1080 - 222 * scale) / 2;
			double hard = frame.hardEdgeShare(panelX + 148 * scale, panelY + 54 * scale, 122 * scale, 66 * scale);
			check(String.format(java.util.Locale.ROOT, "text: smooth edges at GUI scale %d (hard steps %.2f of edges)", scale, hard), hard < 0.3);
			context.runOnClient(minecraft -> client.platform().screens().close());
			context.waitTicks(5);
		}

		context.runOnClient(minecraft -> {
			minecraft.options.guiScale().set(originalScale);
			minecraft.resizeGui();
		});
		context.getInput().resizeWindow(854, 480);
		context.waitTicks(5);
	}
}
