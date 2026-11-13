package dev.wisteria.modules._template;

import dev.wisteria.event.Events;
import dev.wisteria.module.Category;
import dev.wisteria.module.Module;
import dev.wisteria.setting.BoolSetting;
import dev.wisteria.setting.ColorSetting;
import dev.wisteria.setting.DoubleSetting;
import dev.wisteria.setting.EnumSetting;
import net.minecraft.client.player.LocalPlayer;

/**
 * Copy this folder to start a module; see docs/module-api.md. It is not registered in Modules, so it never
 * shows up in the game, but it compiles with everything else and a unit test runs it, so it cannot rot.
 *
 * <p>Source: write here where ported logic comes from and under which licence, e.g.
 * "Adapted from Wurst7 (GPL-3.0), net.wurstclient.hacks.SomeHack".
 */
public final class TemplateModule extends Module {
	public enum Mode {
		GENTLE, FIRM
	}

	// Settings: declared as fields, registered with add(). Name, description, default, then limits.
	// Defaults stay within what Grim allows; aggressive options default to off and say so in the description.
	private final DoubleSetting range = this.add(new DoubleSetting("Range", "How far it reaches, in blocks", 4.0, 1.0, 6.0, 0.5));
	private final EnumSetting<Mode> mode = this.add(new EnumSetting<>("Mode", "How it behaves", Mode.GENTLE));
	private final BoolSetting aggressive = this.add(new BoolSetting("Aggressive", "Experimental: may be flagged by anticheats", false));
	private final ColorSetting color = this.add(new ColorSetting("Color", "Highlight colour", 0xFFB79CFF));

	private int ticks;

	public TemplateModule() {
		// Name (shown in the GUI, also the command: .template), description (the GUI hint), category.
		super("Template", "Shows how a module is put together", Category.MISC);
		// Handlers only run while the module is enabled.
		this.on(Events.Tick.class, event -> this.tick());
		this.on(Events.Render3D.class, this::render);
		this.on(Events.Leave.class, event -> this.ticks = 0);
	}

	@Override
	protected void onEnable() {
		this.ticks = 0;
	}

	@Override
	protected void onDisable() {
		// Undo anything the module changed in the game.
	}

	private void tick() {
		this.ticks++;
		LocalPlayer player = this.player();
		if (player == null) {
			return;
		}

		// Game logic goes here: read state from player, level() and mc(), then act.
	}

	private void render(Events.Render3D event) {
		LocalPlayer player = this.player();
		if (player == null) {
			return;
		}

		// Gather plain data from the game, let a pure function decide, apply the result.
		double[] box = ring(player.getX(), player.getY(), player.getZ(), this.range.get(), this.mode.get());
		event.renderer().box(box[0], box[1], box[2], box[3], box[4], box[5], this.color.get(), 1.5F, 0, true);
	}

	/**
	 * The decision, as a pure function: numbers in, numbers out, no game calls. The unit test checks it
	 * directly, without a world (see docs/module-api.md, "Логика отдельно от игры").
	 *
	 * @return minX, minY, minZ, maxX, maxY, maxZ of the ring drawn around the player
	 */
	static double[] ring(double x, double y, double z, double range, Mode mode) {
		double r = mode == Mode.FIRM ? range : range * 0.75;
		return new double[] {x - r, y, z - r, x + r, y + 0.05, z + r};
	}

	/** Ticks since it was switched on: for the unit test. */
	public int ticks() {
		return this.ticks;
	}

	public Mode mode() {
		return this.mode.get();
	}

	public boolean aggressive() {
		return this.aggressive.get();
	}
}
