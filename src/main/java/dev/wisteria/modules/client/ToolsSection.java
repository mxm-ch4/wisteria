package dev.wisteria.modules.client;

import dev.wisteria.setting.ActionSetting;
import dev.wisteria.setting.InputSetting;

/** Screens and measurements: the design reference, the HUD editor, the box benchmark, the command list. */
public final class ToolsSection extends ClientSection {
	public ToolsSection() {
		super("Tools", "Design screen, HUD editor, benchmark, help");
		this.add(new ActionSetting("Design Screen", "The design system reference screen", "Open", () -> this.client().commands().execute("design", this.client().platform().chat()::print)).withNote("opens another screen; covered by its own test"));
		this.add(new ActionSetting("HUD Editor", "Arrange the HUD", "Open", () -> this.client().commands().execute("hud", this.client().platform().chat()::print)).withNote("opens another screen; covered by its own test"));
		this.add(new InputSetting("Bench Boxes", "Draw this many boxes for 5 seconds and report the frame rate (in a world)", "10000", "Run",
			count -> this.client().commands().run("bench boxes " + (count.isEmpty() ? "10000" : count))));
		this.add(new ActionSetting("Help", "Lists every command in chat", "Show", () -> this.client().commands().execute("help", this.client().platform().chat()::print)));
	}
}
