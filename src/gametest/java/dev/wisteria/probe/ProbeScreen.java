package dev.wisteria.probe;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A bare screen: vanilla blurs the world behind it, we draw our own panel on top. */
public final class ProbeScreen extends Screen {
	public ProbeScreen() {
		super(Component.literal("Wisteria probe"));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int width = 220;
		int height = 96;
		int x = (this.width - width) / 2;
		int y = (this.height - height) / 2;
		Probe.roundedRect(graphics, x, y, width, height, 12, 0xCC17131F);
		Probe.roundedRect(graphics, x + 12, y + 60, 90, 22, 11, Probe.VIOLET);
		Probe.roundedRect(graphics, x + 110, y + 60, 98, 22, 6, 0xFF2E2740);
		graphics.centeredText(this.font, "Wisteria", this.width / 2, y + 14, Probe.PETAL);
		graphics.centeredText(this.font, "blurred world behind, custom shader on top", this.width / 2, y + 30, Probe.MUTED);
		graphics.text(this.font, "accent", x + 40, y + 67, 0xFF0E0B16, false);
		graphics.text(this.font, "surface", x + 140, y + 67, Probe.TEXT, false);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
