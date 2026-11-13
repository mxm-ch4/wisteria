package dev.wisteria.ui.gui;

import dev.wisteria.platform.ScreenBackend.MouseButton;
import dev.wisteria.ui.gui.Controls.Control;
import dev.wisteria.ui.gui.Controls.Kind;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Operates every control of a screen the way a player would and checks that each one responds: its state must
 * change when it is enabled and must not when it is disabled. Opens expanders and dropdowns to reach what is
 * inside, then closes them again. Used by the unit tests and by the in-game gate, each with its own Driver.
 */
public final class ControlWalker {
	/** How the walker reaches the screen: directly in unit tests, through the real mouse and keyboard in the game. */
	public interface Driver {
		/** The controls of the latest frame, after the previous action has had time to show. */
		List<Control> controls();

		void click(float x, float y, MouseButton button);

		void escape();

		/** Reads the control's state on the thread that owns it. */
		String state(Control control);
	}

	/**
	 * @param tested ids of every control that responded as it should
	 * @param failures what went wrong, one line each
	 * @param skipped ids not operated, with the control's note saying why
	 */
	public record Result(List<String> tested, List<String> failures, List<String> skipped) {
		public boolean passed() {
			return this.failures.isEmpty();
		}
	}

	private final Driver driver;
	private final Set<String> done = new LinkedHashSet<>();
	private final List<String> tested = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();
	private final List<String> skipped = new ArrayList<>();

	public ControlWalker(Driver driver) {
		this.driver = driver;
	}

	/** Walks every control whose group starts at {@code rootGroup} ("" for the whole screen). */
	public Result walk(String rootGroup) {
		this.walkGroup(rootGroup);
		return new Result(List.copyOf(this.tested), List.copyOf(this.failures), List.copyOf(this.skipped));
	}

	private @Nullable Control find(String id) {
		for (Control control : this.driver.controls()) {
			if (control.id().equals(id)) {
				return control;
			}
		}

		return null;
	}

	private void walkGroup(String group) {
		while (true) {
			Control next = null;
			List<String> hidden = new ArrayList<>();
			for (Control control : this.driver.controls()) {
				if (!control.group().equals(group) || this.done.contains(control.id())) {
					continue;
				}

				if (control.visible()) {
					next = control;
					break;
				}

				hidden.add(control.id());
			}

			if (next == null) {
				for (String id : hidden) {
					this.done.add(id);
					this.failures.add(id + ": never visible, cannot be reached");
				}

				return;
			}

			this.done.add(next.id());
			if (next.note() != null) {
				this.skipped.add(next.id() + " (" + next.note() + ")");
				continue;
			}

			this.operate(next);
		}
	}

	private void click(Control control, float fx, float fy, MouseButton button) {
		this.driver.click(control.x() + control.width() * fx, control.y() + control.height() * fy, button);
	}

	private void expect(Control control, boolean ok, String what) {
		if (ok) {
			if (!this.tested.contains(control.id())) {
				this.tested.add(control.id());
			}
		} else {
			this.failures.add(control.id() + ": " + what);
		}
	}

	private void operate(Control control) {
		String before = this.driver.state(control);
		if (!control.enabled()) {
			this.click(control, 0.5F, 0.5F, MouseButton.LEFT);
			this.expect(control, Objects.equals(before, this.driver.state(control)), "disabled, but a click changed it");
			return;
		}

		switch (control.kind()) {
			case BUTTON -> {
				this.click(control, 0.5F, 0.5F, MouseButton.LEFT);
				this.expect(control, !Objects.equals(before, this.driver.state(control)), "click did nothing");
			}
			case TOGGLE -> {
				this.click(control, 0.5F, 0.5F, MouseButton.LEFT);
				String after = this.driver.state(control);
				this.expect(control, !Objects.equals(before, after), "click did not flip it");
				this.click(control, 0.5F, 0.5F, MouseButton.LEFT);
				this.expect(control, Objects.equals(before, this.driver.state(control)), "second click did not flip it back");
			}
			case HEADER -> {
				this.click(control, 0.4F, 0.5F, MouseButton.RIGHT);
				this.expect(control, !Objects.equals(before, this.driver.state(control)), "right click did not fold it");
				this.click(control, 0.4F, 0.5F, MouseButton.RIGHT);
				this.expect(control, Objects.equals(before, this.driver.state(control)), "right click did not unfold it");
				// A header's group is its panel: walk the modules inside.
				this.walkGroup(control.id());
			}
			case EXPANDER -> {
				this.click(control, 0.5F, 0.5F, MouseButton.LEFT);
				this.expect(control, !Objects.equals(before, this.driver.state(control)), "click did not open it");
				this.walkGroup(control.id());
				Control again = this.find(control.id());
				if (again != null && !Objects.equals(before, this.driver.state(again))) {
					this.click(again, 0.5F, 0.5F, MouseButton.LEFT);
					this.expect(control, Objects.equals(before, this.driver.state(again)), "click did not close it");
				}
			}
			case DROPDOWN -> this.dropdown(control, before);
			case OPTION -> this.failures.add(control.id() + ": option outside an open dropdown");
			case SLIDER, AREA -> {
				float low = 0.2F;
				float high = 0.8F;
				this.click(control, low, control.kind() == Kind.AREA ? low : 0.5F, MouseButton.LEFT);
				String first = this.driver.state(control);
				this.click(control, high, control.kind() == Kind.AREA ? high : 0.5F, MouseButton.LEFT);
				String second = this.driver.state(control);
				this.expect(control, !Objects.equals(first, second), "clicking at 20% and at 80% gave the same value " + first);
			}
			case FIELD -> {
				this.click(control, 0.5F, 0.5F, MouseButton.LEFT);
				this.expect(control, !Objects.equals(before, this.driver.state(control)), "click did not focus it");
				this.driver.escape();
				this.expect(control, Objects.equals(before, this.driver.state(control)), "Escape did not leave it");
			}
			case KEY -> {
				this.click(control, 0.5F, 0.5F, MouseButton.LEFT);
				this.expect(control, !Objects.equals(before, this.driver.state(control)), "click did not start recording");
				Control again = this.find(control.id());
				if (again != null) {
					this.click(again, 0.5F, 0.5F, MouseButton.LEFT);
					this.expect(control, Objects.equals(before, this.driver.state(again)), "second click did not stop recording");
				}
			}
		}
	}

	// Choosing an option closes the dropdown, so it is reopened before each one.
	private void dropdown(Control control, String before) {
		this.click(control, 0.5F, 0.5F, MouseButton.LEFT);
		this.expect(control, !Objects.equals(before, this.driver.state(control)), "click did not open it");
		List<String> options = new ArrayList<>();
		for (Control child : this.driver.controls()) {
			if (child.group().equals(control.id())) {
				options.add(child.id());
			}
		}

		if (options.isEmpty()) {
			this.failures.add(control.id() + ": opened without options");
		}

		for (String id : options) {
			this.done.add(id);
			Control option = this.find(id);
			if (option == null || !option.visible()) {
				Control parent = this.find(control.id());
				if (parent != null) {
					this.click(parent, 0.5F, 0.5F, MouseButton.LEFT);
				}

				option = this.find(id);
			}

			if (option == null || !option.visible()) {
				this.failures.add(id + ": not visible after opening " + control.id());
				continue;
			}

			this.click(option, 0.5F, 0.5F, MouseButton.LEFT);
			this.expect(option, "selected".equals(this.driver.state(option)), "click did not select it");
		}

		Control again = this.find(control.id());
		if (again != null && !Objects.equals(before, this.driver.state(again))) {
			this.click(again, 0.5F, 0.5F, MouseButton.LEFT);
		}
	}
}
