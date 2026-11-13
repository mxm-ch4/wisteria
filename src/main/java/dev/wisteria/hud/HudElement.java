package dev.wisteria.hud;

import dev.wisteria.module.Category;
import dev.wisteria.module.Module;
import dev.wisteria.setting.FloatSetting;
import org.jspecify.annotations.Nullable;

/**
 * A HUD element: a module in the HUD category that draws one block of information. The {@link HudManager}
 * places it from its anchor and offset (set in the HUD editor), scales it, and fades it in and out; the element
 * only measures and draws itself at 0, 0.
 */
public abstract class HudElement extends Module {
	private final HudLayout.Placement defaultPlacement;
	private final FloatSetting scale = this.add(new FloatSetting("Scale", "Size of this element", 1.0F, 0.5F, 3.0F, 0.25F));

	protected HudElement(String name, String description, Anchor anchor, float dx, float dy) {
		super(name, description, Category.HUD);
		this.defaultPlacement = new HudLayout.Placement(anchor, dx, dy);
	}

	/**
	 * Size in GUI pixels before scaling, or null when there is nothing to show right now (no target, no
	 * effects); in the editor ({@link HudContext#preview()}) return the size of sample content instead.
	 */
	protected abstract float @Nullable [] measure(HudContext context);

	/** Draws at 0, 0 in the size {@link #measure} gave. */
	protected abstract void draw(HudContext context, float width, float height);

	public final HudLayout.Placement defaultPlacement() {
		return this.defaultPlacement;
	}

	public final float scale() {
		return this.scale.get();
	}

	final float @Nullable [] measureFor(HudContext context) {
		return this.measure(context);
	}

	final void drawFor(HudContext context, float width, float height) {
		this.draw(context, width, height);
	}
}
