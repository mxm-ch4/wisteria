package dev.wisteria.gate;

import static dev.wisteria.gate.GameTestKit.chat;
import static dev.wisteria.gate.GameTestKit.check;

import com.mojang.blaze3d.platform.InputConstants;
import dev.wisteria.Client;
import dev.wisteria.Wisteria;
import dev.wisteria.modules.clickgui.ClickGui;
import dev.wisteria.platform.ScreenBackend.MouseButton;
import dev.wisteria.ui.DesignScreen;
import dev.wisteria.ui.gui.ClickGuiScreen;
import dev.wisteria.ui.gui.ControlWalker;
import dev.wisteria.ui.gui.Controls;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clicks every control of the ClickGUI and the design screen with the real mouse at 1920x1080, GUI scale 2,
 * and fails if any of them does not respond. Works on a throwaway profile and puts everything back afterwards.
 */
public final class ClickEverythingGameTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("WisteriaGate");

	/** The real mouse and keyboard; waits long enough after each action for animations to settle. */
	private record RealInput(ClientGameTestContext context, Supplier<Controls> source, float scale) implements ControlWalker.Driver {
		@Override
		public List<Controls.Control> controls() {
			return this.context.computeOnClient(minecraft -> this.source.get().snapshot());
		}

		@Override
		public void click(float x, float y, MouseButton button) {
			this.context.getInput().setCursorPos(x * this.scale, y * this.scale);
			this.context.waitTicks(1);
			this.context.getInput().pressMouse(switch (button) {
				case LEFT -> InputConstants.MOUSE_BUTTON_LEFT;
				case RIGHT -> InputConstants.MOUSE_BUTTON_RIGHT;
				case MIDDLE -> InputConstants.MOUSE_BUTTON_MIDDLE;
			});
			this.context.waitTicks(6);
		}

		@Override
		public void escape() {
			this.context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			this.context.waitTicks(4);
		}

		@Override
		public String state(Controls.Control control) {
			return this.context.computeOnClient(minecraft -> control.state().get());
		}

		@Override
		public void scroll(float x, float y, float amount) {
			this.context.getInput().setCursorPos(x * this.scale, y * this.scale);
			this.context.waitTicks(1);
			this.context.getInput().scroll(amount);
			this.context.waitTicks(3);
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (GameTestKit.isAuditPhase()) {
			return;
		}

		if (GameTestKit.isRestartPhase()) {
			return;
		}

		Client client = Wisteria.client();
		String profile = context.computeOnClient(minecraft -> client.config().profile());
		String theme = context.computeOnClient(minecraft -> client.themes().presetId());
		int originalScale = context.computeOnClient(minecraft -> minecraft.options.guiScale().get());
		context.getInput().resizeWindow(1920, 1080);
		context.runOnClient(minecraft -> {
			minecraft.options.guiScale().set(2);
			minecraft.resizeGui();
		});
		context.waitTicks(5);
		float scale = GameTestKit.guiScale(context);

		try (TestSingleplayerContext world = GameTestKit.newWorld(context)) {
			chat(context, ".profile save clicks");
			context.getInput().pressKey(InputConstants.KEY_RSHIFT);
			context.waitTicks(15);
			ClickGuiScreen gui = context.computeOnClient(minecraft -> ((ClickGui)client.modules().get("clickgui")).screen());
			check("clicks: ClickGUI open", gui != null);
			ControlWalker.Result result = new ControlWalker(new RealInput(context, gui::controls, scale)).walk("");
			LOG.info("clicks: ClickGUI {} controls responded, {} skipped: {}", result.tested().size(), result.skipped().size(), result.skipped());
			check("clicks: every ClickGUI control responds " + result.failures(), result.passed());
			check("clicks: ClickGUI reached every panel, module and setting (" + result.tested().size() + ")", result.tested().size() >= 60);
			context.getInput().pressKey(InputConstants.KEY_RSHIFT);
			context.waitTicks(5);

			DesignScreen design = context.computeOnClient(minecraft -> new DesignScreen(client));
			context.runOnClient(minecraft -> client.platform().screens().open(design));
			context.waitTicks(15);
			ControlWalker.Result designResult = new ControlWalker(new RealInput(context, design::controls, scale)).walk("");
			LOG.info("clicks: design screen {} controls responded", designResult.tested().size());
			check("clicks: every design screen control responds " + designResult.failures(), designResult.passed() && designResult.tested().size() >= 5);
			context.runOnClient(minecraft -> client.platform().screens().close());
			context.waitTicks(5);

			chat(context, ".profile load " + profile);
			chat(context, ".profile delete clicks");
			context.runOnClient(minecraft -> client.themes().usePreset(theme));
		}

		context.runOnClient(minecraft -> {
			minecraft.options.guiScale().set(originalScale);
			minecraft.resizeGui();
		});
		context.getInput().resizeWindow(854, 480);
		context.waitTicks(5);
	}
}
