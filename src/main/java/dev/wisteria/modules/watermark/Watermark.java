package dev.wisteria.modules.watermark;

import dev.wisteria.Wisteria;
import dev.wisteria.render.Renderer2D;
import dev.wisteria.event.Events;
import dev.wisteria.module.Category;
import dev.wisteria.module.Module;
import dev.wisteria.setting.BoolSetting;
import dev.wisteria.setting.ColorSetting;
import dev.wisteria.setting.EnumSetting;
import dev.wisteria.setting.FloatSetting;
import dev.wisteria.setting.StringSetting;
import dev.wisteria.ui.Metrics.Type;

/**
 * The client's name in a corner of the screen. A plain stand-in until the HUD stage brings draggable
 * elements; it exists now so the core has something 2D to switch on and configure.
 */
public final class Watermark extends Module {
	public enum Corner {
		TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
	}

	private final StringSetting text = this.add(new StringSetting("Text", "What to show", Wisteria.NAME, 48));
	private final EnumSetting<Corner> corner = this.add(new EnumSetting<>("Corner", "Where on the screen", Corner.TOP_LEFT));
	private final ColorSetting color = this.add(new ColorSetting("Color", "Text colour", 0xFFB79CFF));
	private final FloatSetting scale = this.add(new FloatSetting("Scale", "Text size", 1.0F, 0.5F, 4.0F, 0.25F));
	private final BoolSetting background = this.add(new BoolSetting("Background", "Rounded panel behind the text", true));
	private final BoolSetting showVersion = this.add(new BoolSetting("Show Version", "Append the Minecraft version", true));

	public Watermark() {
		super("Watermark", "Shows the client name on screen", Category.RENDER);
		this.on(Events.Render2D.class, this::render);
	}

	private void render(Events.Render2D event) {
		Renderer2D renderer = event.renderer();
		String label = this.text.get();
		if (this.showVersion.get()) {
			label = label + "  " + this.client().platform().minecraftVersion();
		}

		float size = Type.LABEL.size() * this.scale.get();
		float padding = this.background.get() ? 5.0F * this.scale.get() : 0.0F;
		float width = renderer.textWidth(Type.LABEL.face(), size, label) + padding * 2.0F;
		float height = renderer.lineHeight(Type.LABEL.face(), size) + padding * 2.0F;
		float margin = 6.0F;
		Corner where = this.corner.get();
		boolean right = where == Corner.TOP_RIGHT || where == Corner.BOTTOM_RIGHT;
		boolean bottom = where == Corner.BOTTOM_LEFT || where == Corner.BOTTOM_RIGHT;
		float x = right ? renderer.width() - width - margin : margin;
		float y = bottom ? renderer.height() - height - margin : margin;

		if (this.background.get()) {
			renderer.roundedRect(x, y, width, height, Math.min(height / 2.0F, 6.0F), 0xD917131F);
		}

		renderer.text(Type.LABEL.face(), size, label, x + padding, y + padding, this.color.get());
	}
}
