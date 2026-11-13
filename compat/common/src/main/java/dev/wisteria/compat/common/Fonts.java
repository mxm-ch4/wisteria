package dev.wisteria.compat.common;

import dev.wisteria.compat.FontFace;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * The client's typefaces as game fonts: assets/wisteria/font/*.json point the game's own TrueType provider
 * at the bundled files, so glyphs go through the same atlas and pipelines as vanilla text.
 */
final class Fonts {
	/** The size the font JSONs ask for; they oversample by 4, so text stays sharp up to four times this. */
	static final float BASE_SIZE = 16.0F;
	/** The game places every font's baseline this far below the y it is asked to draw at. */
	private static final float GAME_BASELINE = 7.0F;
	/** Where we put the baseline inside our own line box, as a fraction of the font size. */
	private static final float BASELINE = 0.94F;
	private static final float LINE_HEIGHT = 1.25F;

	private static final Style REGULAR = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("wisteria", "regular")));
	private static final Style SEMIBOLD = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("wisteria", "semibold")));
	private static final Style MONO = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("wisteria", "mono")));

	private Fonts() {
	}

	static Component styled(FontFace face, String text) {
		Style style = switch (face) {
			case REGULAR -> REGULAR;
			case SEMIBOLD -> SEMIBOLD;
			case MONO -> MONO;
		};
		return Component.literal(text).withStyle(style);
	}

	/** How far to move down from the top of our line box so the game's baseline lands on ours. */
	static float topOffset(FontFace face, float size) {
		return size * BASELINE - GAME_BASELINE * size / BASE_SIZE;
	}

	static float lineHeight(float size) {
		return size * LINE_HEIGHT;
	}
}
