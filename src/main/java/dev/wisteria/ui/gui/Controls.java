package dev.wisteria.ui.gui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Every interactive control drawn in a frame: id, rectangle, kind and a way to read its state. Screens rebuild
 * it each frame. It decides hover (topmost control under the cursor, from the previous frame) and press
 * (the control the mouse went down on), and {@link ControlWalker} uses it to click through a whole screen.
 */
public final class Controls {
	/** How a control is operated; tells {@link ControlWalker} what a click should do to its state. */
	public enum Kind {
		/** Left click does something and changes its state: buttons, module rows, theme dots. */
		BUTTON,
		/** Left click flips its state. */
		TOGGLE,
		/** Left click opens or closes it; its children are the controls whose group is its id. */
		EXPANDER,
		/** Like EXPANDER, but its children are OPTIONs and choosing one closes it. */
		DROPDOWN,
		/** An entry in a DROPDOWN: clicking it selects it. */
		OPTION,
		/** Right click folds and unfolds a panel; left drag moves it. */
		HEADER,
		/** Horizontal: where you click sets the value. */
		SLIDER,
		/** Two-dimensional: where you click sets the value. */
		AREA,
		/** Click focuses it for typing; Escape leaves. */
		FIELD,
		/** Click starts recording a key; clicking again stops. */
		KEY
	}

	/**
	 * @param group id of the control this one belongs to (shown when that one is open); "" for top level
	 * @param visible false when it is scrolled or clipped out of view
	 * @param note why a walker must not click it, or null
	 */
	public record Control(String id, String group, Kind kind, float x, float y, float width, float height,
		boolean enabled, boolean visible, Supplier<String> state, @Nullable String note) {
		public boolean contains(float px, float py) {
			return Ui.inside(px, py, this.x, this.y, this.width, this.height);
		}

		public float centerX() {
			return this.x + this.width / 2.0F;
		}

		public float centerY() {
			return this.y + this.height / 2.0F;
		}
	}

	/** What a control should look like right now. */
	public record State(boolean hovered, boolean pressed, boolean enabled) {
		public static final State IDLE = new State(false, false, true);
	}

	private final List<Control> building = new ArrayList<>();
	private final ArrayDeque<float[]> clips = new ArrayDeque<>();
	private final ArrayDeque<String> groups = new ArrayDeque<>();
	private volatile List<Control> lastFrame = List.of();
	private @Nullable String hoveredId;
	private @Nullable String pressedId;
	private float mouseX;
	private float mouseY;

	/** Starts a frame: works out from the last frame which control is under the cursor. */
	public void begin(float mouseX, float mouseY) {
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		Control top = this.at(mouseX, mouseY);
		this.hoveredId = top != null ? top.id() : null;
		this.building.clear();
		this.clips.clear();
		this.groups.clear();
	}

	public void end() {
		this.lastFrame = List.copyOf(this.building);
	}

	/** The controls of the last complete frame. Safe to read from another thread. */
	public List<Control> snapshot() {
		return this.lastFrame;
	}

	/** The topmost visible control at a point in the last frame: later controls are drawn over earlier ones. */
	public @Nullable Control at(float px, float py) {
		List<Control> controls = this.lastFrame;
		for (int i = controls.size() - 1; i >= 0; i--) {
			Control control = controls.get(i);
			if (control.visible() && control.contains(px, py)) {
				return control;
			}
		}

		return null;
	}

	public void press(float px, float py) {
		Control control = this.at(px, py);
		this.pressedId = control != null && control.enabled() ? control.id() : null;
	}

	public void release() {
		this.pressedId = null;
	}

	/** Controls registered until the matching {@link #popGroup()} belong to {@code id}. */
	public void pushGroup(String id) {
		this.groups.push(id);
	}

	public void popGroup() {
		this.groups.pop();
	}

	public String group() {
		String group = this.groups.peek();
		return group != null ? group : "";
	}

	/** Controls outside this rectangle until {@link #popClip()} count as not visible. Nested clips intersect. */
	public void pushClip(float x, float y, float width, float height) {
		float[] parent = this.clips.peek();
		float[] clip = {x, y, x + width, y + height};
		if (parent != null) {
			clip = new float[] {Math.max(clip[0], parent[0]), Math.max(clip[1], parent[1]), Math.min(clip[2], parent[2]), Math.min(clip[3], parent[3])};
		}

		this.clips.push(clip);
	}

	public void popClip() {
		this.clips.pop();
	}

	public State register(String id, Kind kind, float x, float y, float width, float height, boolean enabled,
		Supplier<String> state, @Nullable String note) {
		float[] clip = this.clips.peek();
		float cx = x + width / 2.0F;
		float cy = y + height / 2.0F;
		boolean visible = width > 0.5F && height > 0.5F
			&& (clip == null || (cx >= clip[0] && cx < clip[2] && cy >= clip[1] && cy < clip[3]));
		this.building.add(new Control(id, this.group(), kind, x, y, width, height, enabled, visible, state, note));
		boolean hovered = visible && id.equals(this.hoveredId) && Ui.inside(this.mouseX, this.mouseY, x, y, width, height);
		boolean pressed = enabled && id.equals(this.pressedId);
		return new State(hovered && enabled, pressed, enabled);
	}

	public State register(String id, Kind kind, float x, float y, float width, float height, Supplier<String> state) {
		return this.register(id, kind, x, y, width, height, true, state, null);
	}
}
