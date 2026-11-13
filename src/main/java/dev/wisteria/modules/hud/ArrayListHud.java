package dev.wisteria.modules.hud;

import dev.wisteria.hud.Anchor;
import dev.wisteria.hud.HudContext;
import dev.wisteria.hud.HudElement;
import dev.wisteria.module.Module;
import dev.wisteria.setting.BoolSetting;
import dev.wisteria.ui.Animation;
import dev.wisteria.ui.Colors;
import dev.wisteria.ui.Easing;
import dev.wisteria.ui.Metrics.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/** The modules that are on, longest name first, sliding in and out, coloured along the brand gradient. */
public final class ArrayListHud extends HudElement {
	private static final float BAR = 2.0F;
	private static final List<String> SAMPLE = List.of("Xray", "FullBright", "ESP");

	private final BoolSetting background = this.add(new BoolSetting("Background", "A strip behind each name", true));
	private final Map<Module, Animation> shown = new IdentityHashMap<>();
	private final List<Line> lines = new ArrayList<>();

	private record Line(String name, float presence) {
	}

	public ArrayListHud() {
		super("ArrayList", "The modules that are on, longest first", Anchor.TOP_RIGHT, -6.0F, 6.0F);
	}

	/** Longest first, then by name; pure, for the unit test. */
	public static List<String> order(List<String> names, ToDoubleFunction<String> width) {
		return names.stream().sorted(Comparator.comparingDouble(width).reversed().thenComparing(Comparator.naturalOrder())).toList();
	}

	@Override
	protected float[] measure(HudContext c) {
		this.lines.clear();
		List<String> names = new ArrayList<>();
		Map<String, Float> presence = new java.util.HashMap<>();
		for (Module module : this.client().modules().all()) {
			if (module instanceof HudElement || !module.savesEnabled()) {
				continue;
			}

			Animation animation = this.shown.computeIfAbsent(module, key -> new Animation(key.isEnabled() ? 1.0F : 0.0F, Animation.NORMAL, Easing.OUT_CUBIC));
			animation.animateTo(module.isEnabled());
			if (animation.value() > 0.01F) {
				names.add(module.name());
				presence.put(module.name(), animation.value());
			}
		}

		if (names.isEmpty() && c.preview()) {
			SAMPLE.forEach(name -> presence.put(name, 1.0F));
			names.addAll(SAMPLE);
		}

		if (names.isEmpty()) {
			return null;
		}

		float width = 0.0F;
		float height = 0.0F;
		for (String name : order(names, name -> c.width(Type.LABEL, name))) {
			float p = presence.get(name);
			this.lines.add(new Line(name, p));
			width = Math.max(width, c.width(Type.LABEL, name) + HudContext.PAD * 2.0F + BAR);
			height += this.lineHeight(c) * p;
		}

		return new float[] {width, height};
	}

	private float lineHeight(HudContext c) {
		return c.line(Type.LABEL) + 3.0F;
	}

	@Override
	protected void draw(HudContext c, float width, float height) {
		boolean right = c.anchor().right();
		boolean up = c.anchor().bottom();
		float y = up ? height : 0.0F;
		int count = this.lines.size();
		for (int i = 0; i < count; i++) {
			Line line = this.lines.get(i);
			float lineHeight = this.lineHeight(c) * line.presence();
			if (up) {
				y -= lineHeight;
			}

			float textWidth = c.width(Type.LABEL, line.name());
			float stripWidth = textWidth + HudContext.PAD * 2.0F + BAR;
			// Slides in from the side it is pinned to.
			float slide = (1.0F - line.presence()) * stripWidth;
			float x = right ? width - stripWidth + slide : -slide;
			int colour = Colors.mix(c.theme().gradientStart(), c.theme().gradientEnd(), count == 1 ? 0.0F : i / (float)(count - 1));
			int fade = Colors.fade(0xFFFFFFFF, line.presence());
			c.renderer().pushScissor(0.0F, y, width, lineHeight);
			if (this.background.get()) {
				c.renderer().rect(x, y, stripWidth, lineHeight, c.color(Colors.fade(Colors.withAlpha(c.theme().surface(), 0xD0), line.presence())));
			}

			c.renderer().rect(right ? x + stripWidth - BAR : x, y, BAR, lineHeight, c.color(Colors.fade(colour, line.presence())));
			float textX = right ? x + HudContext.PAD : x + BAR + HudContext.PAD;
			c.text(Type.LABEL, line.name(), textX, y + 1.5F, Colors.fade(Colors.mix(colour, c.theme().text(), 0.35F), Colors.alpha(fade) / 255.0F));
			c.renderer().popScissor();
			if (!up) {
				y += lineHeight;
			}
		}
	}
}
