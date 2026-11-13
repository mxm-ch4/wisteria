package dev.wisteria.gate;

import static dev.wisteria.gate.GameTestKit.chat;
import static dev.wisteria.gate.GameTestKit.check;
import static dev.wisteria.gate.GameTestKit.grabLastFrame;

import com.mojang.blaze3d.platform.InputConstants;
import dev.wisteria.Client;
import dev.wisteria.Wisteria;
import dev.wisteria.gate.GameTestKit.Frame;
import dev.wisteria.hud.Anchor;
import dev.wisteria.hud.HudEditorScreen;
import dev.wisteria.hud.HudLayout;
import dev.wisteria.hud.HudManager;
import dev.wisteria.modules.hud.HudEditor;
import dev.wisteria.modules.hud.Tps;
import dev.wisteria.ui.gui.Controls;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 4: every HUD element shows up in a real frame with real data, the editor moves one with the real mouse
 * and snaps it to the edge, and the second launch finds it where it was left.
 */
public final class HudGameTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("WisteriaGate");
	private static final List<String> ELEMENTS = List.of("watermark", "arraylist", "coordinates", "direction", "fps", "tps",
		"target", "potions", "keybinds", "notifications");

	@Override
	public void runTest(ClientGameTestContext context) {
		Client client = Wisteria.client();
		if (GameTestKit.isRestartPhase()) {
			context.runOnClient(minecraft -> {
				HudLayout.Placement placement = client.hud().placement(client.hud().element("coordinates"));
				check("hud restart: Coordinates still on", client.modules().get("coordinates").isEnabled());
				check("hud restart: Coordinates where the editor left it (" + placement + ")",
					placement.anchor() == Anchor.TOP_RIGHT && Math.abs(placement.dx()) < 0.01F);
				float coordinates = client.hud().element("coordinates").scale();
				float potions = client.hud().element("potions").scale();
				check("hud restart: sizes kept (Coordinates " + coordinates + ", Potions " + potions + ")",
					Math.abs(coordinates - 1.5F) < 0.001F && Math.abs(potions - 1.05F) < 0.001F);
			});
			return;
		}

		// A clean HUD for the screenshots: the gate's own panels step aside.
		GateProbe probe = GateProbe.current;
		if (probe != null) {
			probe.hidden = true;
		}

		try (TestSingleplayerContext world = GameTestKit.newWorld(context)) {
			this.elements(context, world, client);
			this.editor(context, client);
			this.sizes(context, client);
			// Leave Coordinates on for the restart check and Watermark as GateTest left it (on, bottom right);
			// the rest would sit over later tests' frames.
			for (String id : ELEMENTS) {
				if (!id.equals("coordinates") && !id.equals("watermark")) {
					chat(context, ".toggle " + id + " off");
				}
			}

			chat(context, ".toggle esp off");
			chat(context, ".hud watermark bottom-right");
			context.waitTicks(10);
		} finally {
			if (probe != null) {
				probe.hidden = false;
			}
		}
	}

	private void elements(ClientGameTestContext context, TestSingleplayerContext world, Client client) {
		BlockPos feet = context.computeOnClient(minecraft -> minecraft.player.blockPosition());
		world.getServer().runCommand("time set noon");
		world.getServer().runCommand("tp @a " + (feet.getX() + 0.5) + " " + feet.getY() + " " + (feet.getZ() + 0.5) + " 0 10");
		world.getServer().runCommand("summon minecraft:zombie " + (feet.getX() + 0.5) + " " + feet.getY() + " " + (feet.getZ() + 3.5)
			+ " {NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[180f,0f]}");
		world.getServer().runCommand("effect give @a minecraft:speed 300 1 true");
		context.waitTicks(20);
		Frame off = grabLastFrame(context, client.platform(), "hud-off");

		for (String id : ELEMENTS) {
			chat(context, ".toggle " + id + " on");
		}

		// GateTest pinned Watermark bottom right, where the toasts go; for the screenshot it goes back home.
		chat(context, ".hud watermark top-left");

		// Two server time updates for TPS, then a module switch for a toast and a line in the ArrayList.
		context.waitTicks(50);
		chat(context, ".toggle esp on");
		context.waitTicks(15);
		Frame on = grabLastFrame(context, client.platform(), "hud-elements");
		float scale = GameTestKit.guiScale(context);
		List<HudManager.Drawn> drawn = context.computeOnClient(minecraft -> client.hud().lastDrawn());
		Set<String> shown = drawn.stream().map(item -> item.element().id()).collect(Collectors.toSet());
		LOG.info("hud: drawn {}", drawn.stream().map(item -> item.element().id() + "@" + Math.round(item.rect().x()) + "," + Math.round(item.rect().y())).toList());
		for (String id : ELEMENTS) {
			check("hud: " + id + " drawn with real data", shown.contains(id));
		}

		for (HudManager.Drawn item : drawn) {
			float changed = changedShare(off, on, item.rect(), scale);
			check("hud: " + item.element().id() + " visible in the frame (" + Math.round(changed * 100) + "% of its box changed)", changed > 0.2F);
		}

		double tps = context.computeOnClient(minecraft -> ((Tps)client.modules().get("tps")).tps());
		LOG.info("hud: TPS reads {}", tps);
		check("hud: TPS measured from the server's time updates (" + tps + ")", tps > 10.0 && tps <= 20.0);
	}

	private void editor(ClientGameTestContext context, Client client) {
		chat(context, ".hud");
		context.waitTicks(5);
		HudEditorScreen editor = context.computeOnClient(minecraft -> ((HudEditor)client.modules().get("hudeditor")).screen());
		check("hud editor: .hud opens it", editor != null && client.hud().isEditing());
		float scale = GameTestKit.guiScale(context);
		float guiWidth = context.computeOnClient(minecraft -> (float)minecraft.getWindow().getGuiScaledWidth());
		float guiHeight = context.computeOnClient(minecraft -> (float)minecraft.getWindow().getGuiScaledHeight());
		Controls.Control box = context.computeOnClient(minecraft -> editor.controls().snapshot().stream()
			.filter(control -> control.id().equals("hud:coordinates")).findFirst().orElseThrow());

		// Grab it by the middle and drop it 2 px short of the right edge, in the top third.
		context.getInput().setCursorPos(box.centerX() * scale, box.centerY() * scale);
		context.waitTicks(2);
		context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		float targetX = guiWidth - box.width() / 2.0F - 2.0F;
		float targetY = guiHeight * 0.22F;
		context.getInput().setCursorPos((guiWidth / 2.0F) * scale, (guiHeight / 2.0F) * scale);
		context.waitTicks(2);
		context.getInput().setCursorPos(targetX * scale, targetY * scale);
		context.waitTicks(3);
		grabLastFrame(context, client.platform(), "hud-editor");
		context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		HudLayout.Placement placement = context.computeOnClient(minecraft -> client.hud().placement(client.hud().element("coordinates")));
		LOG.info("hud editor: dropped Coordinates at {}", placement);
		check("hud editor: dragging with the mouse re-anchors it to the top right", placement.anchor() == Anchor.TOP_RIGHT);
		check("hud editor: it snapped onto the right edge", Math.abs(placement.dx()) < 0.01F);

		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(5);
		check("hud editor: Escape closes it", context.computeOnClient(minecraft -> !client.hud().isEditing() && !client.modules().get("hudeditor").isEnabled()));
		Frame moved = grabLastFrame(context, client.platform(), "hud-moved");
		HudManager.Drawn coordinates = context.computeOnClient(minecraft -> client.hud().lastDrawn().stream()
			.filter(item -> item.element().id().equals("coordinates")).findFirst().orElseThrow());
		check("hud editor: Coordinates now drawn against the right edge",
			Math.abs(coordinates.rect().right() - guiWidth) < 0.5F && moved.count(Math.round(coordinates.rect().x() * scale), Math.round(coordinates.rect().y() * scale),
				Math.round(coordinates.rect().width() * scale), Math.round(coordinates.rect().height() * scale), argb -> GameTestKit.luminance(argb) > 200) > 20);
	}

	// Size: the command at both ends of the range, then the real mouse on a corner handle and the wheel.
	private void sizes(ClientGameTestContext context, Client client) {
		float scale = GameTestKit.guiScale(context);
		float guiWidth = context.computeOnClient(minecraft -> (float)minecraft.getWindow().getGuiScaledWidth());
		float guiHeight = context.computeOnClient(minecraft -> (float)minecraft.getWindow().getGuiScaledHeight());
		for (String size : List.of("0.5", "3")) {
			chat(context, ".hud coordinates scale " + size);
			context.waitTicks(5);
			grabLastFrame(context, client.platform(), "hud-scale-" + size);
			HudLayout.Rect rect = this.rectOf(context, client, "coordinates");
			check("hud size: Coordinates at " + size + "x stays on screen and on the right edge (" + rect + ")",
				rect.x() >= 0.0F && rect.y() >= 0.0F && Math.abs(rect.right() - guiWidth) < 0.5F && rect.bottom() <= guiHeight + 0.01F);
		}

		chat(context, ".hud coordinates scale reset");
		chat(context, ".hud");
		context.waitTicks(5);
		HudEditorScreen editor = context.computeOnClient(minecraft -> ((HudEditor)client.modules().get("hudeditor")).screen());
		Controls.Control box = context.computeOnClient(minecraft -> editor.controls().snapshot().stream()
			.filter(control -> control.id().equals("hud:coordinates")).findFirst().orElseThrow());

		// Select it, then pull its bottom-left handle half again as far from the top-right anchor.
		GameTestKit.click(context, box.centerX(), box.centerY(), InputConstants.MOUSE_BUTTON_LEFT, scale);
		context.getInput().setCursorPos(box.x() * scale, (box.y() + box.height()) * scale);
		context.waitTicks(2);
		context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		context.getInput().setCursorPos((box.x() - box.width() * 0.25F) * scale, (box.y() + box.height() * 1.25F) * scale);
		context.waitTicks(2);
		context.getInput().setCursorPos((box.x() - box.width() * 0.5F) * scale, (box.y() + box.height() * 1.5F) * scale);
		context.waitTicks(3);
		grabLastFrame(context, client.platform(), "hud-resize");
		context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		float coordinates = context.computeOnClient(minecraft -> client.hud().element("coordinates").scale());
		check("hud size: dragging a corner handle with the mouse scales Coordinates to 1.5x (" + coordinates + ")", Math.abs(coordinates - 1.5F) < 0.001F);
		HudLayout.Rect grown = this.rectOf(context, client, "coordinates");
		check("hud size: it grew from its anchor, still against the right edge", Math.abs(grown.right() - guiWidth) < 0.5F && Math.abs(grown.y() - box.y()) < 0.5F);

		Controls.Control potions = context.computeOnClient(minecraft -> editor.controls().snapshot().stream()
			.filter(control -> control.id().equals("hud:potions")).findFirst().orElseThrow());
		context.getInput().setCursorPos(potions.centerX() * scale, potions.centerY() * scale);
		context.waitTicks(2);
		context.getInput().scroll(1.0);
		context.waitTicks(2);
		float potionsScale = context.computeOnClient(minecraft -> client.hud().element("potions").scale());
		check("hud size: one wheel notch over Potions is 1.05x (" + potionsScale + ")", Math.abs(potionsScale - 1.05F) < 0.001F);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(5);
	}

	private HudLayout.Rect rectOf(ClientGameTestContext context, Client client, String id) {
		return context.computeOnClient(minecraft -> client.hud().lastDrawn().stream()
			.filter(item -> item.element().id().equals(id)).findFirst().orElseThrow().rect());
	}

	/** Share of the pixels in a GUI-space box that differ between two frames. */
	private static float changedShare(Frame before, Frame after, HudLayout.Rect rect, float scale) {
		int x0 = Math.max(0, Math.round(rect.x() * scale));
		int y0 = Math.max(0, Math.round(rect.y() * scale));
		int x1 = Math.min(after.width(), Math.round(rect.right() * scale));
		int y1 = Math.min(after.height(), Math.round(rect.bottom() * scale));
		int changed = 0;
		int total = 0;
		for (int y = y0; y < y1; y++) {
			for (int x = x0; x < x1; x++) {
				total++;
				changed += GameTestKit.distance(before.pixels()[y * before.width() + x], after.pixels()[y * after.width() + x]) > 24 ? 1 : 0;
			}
		}

		return total == 0 ? 0.0F : changed / (float)total;
	}
}
