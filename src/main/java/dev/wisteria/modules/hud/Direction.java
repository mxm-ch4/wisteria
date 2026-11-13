package dev.wisteria.modules.hud;

import dev.wisteria.hud.Anchor;
import dev.wisteria.hud.HudContext;
import dev.wisteria.hud.HudElement;
import dev.wisteria.setting.BoolSetting;
import dev.wisteria.ui.Metrics.Type;
import java.util.Locale;
import net.minecraft.client.player.LocalPlayer;

/** Which way you face, along which axis, and the biome you stand in. */
public final class Direction extends HudElement {
	private final BoolSetting biome = this.add(new BoolSetting("Biome", "Show the biome", true));
	private String facing = "";
	private String biomeName = "";

	public Direction() {
		super("Direction", "Facing, axis and biome", Anchor.TOP_LEFT, 6.0F, 44.0F);
	}

	/** "South +Z" for a yaw in degrees (0 is south, 90 west, the game's convention); pure, for the unit test. */
	public static String facing(float yaw) {
		float wrapped = ((yaw % 360.0F) + 360.0F) % 360.0F;
		int quarter = Math.round(wrapped / 90.0F) % 4;
		return switch (quarter) {
			case 0 -> "South +Z";
			case 1 -> "West -X";
			case 2 -> "North -Z";
			default -> "East +X";
		};
	}

	/** "minecraft:dark_forest" to "Dark Forest". */
	public static String prettyBiome(String id) {
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		StringBuilder out = new StringBuilder();
		for (String word : path.split("_")) {
			if (!word.isEmpty()) {
				out.append(out.isEmpty() ? "" : " ").append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
			}
		}

		return out.toString();
	}

	@Override
	protected float[] measure(HudContext c) {
		LocalPlayer player = this.player();
		if (player != null) {
			this.facing = facing(player.getYRot());
			this.biomeName = this.biome.get() ? player.level().getBiome(player.blockPosition()).unwrapKey()
				.map(key -> prettyBiome(key.identifier().toString())).orElse("") : "";
		} else if (c.preview()) {
			this.facing = "South +Z";
			this.biomeName = this.biome.get() ? "Plains" : "";
		} else {
			return null;
		}

		float width = c.width(Type.LABEL, this.facing) + (this.biomeName.isEmpty() ? 0.0F : 8.0F + c.width(Type.BODY, this.biomeName));
		return new float[] {width + HudContext.PAD * 2.0F, c.line(Type.LABEL) + HudContext.PAD * 2.0F};
	}

	@Override
	protected void draw(HudContext c, float width, float height) {
		c.panel(0.0F, 0.0F, width, height);
		c.text(Type.LABEL, this.facing, HudContext.PAD, HudContext.PAD, c.theme().text());
		if (!this.biomeName.isEmpty()) {
			float x = HudContext.PAD + c.width(Type.LABEL, this.facing) + 8.0F;
			c.renderer().roundedRect(x - 5.0F, height / 2.0F - 1.0F, 2.0F, 2.0F, 1.0F, c.color(c.theme().textMuted()));
			c.text(Type.BODY, this.biomeName, x, HudContext.PAD, c.theme().textMuted());
		}
	}
}
