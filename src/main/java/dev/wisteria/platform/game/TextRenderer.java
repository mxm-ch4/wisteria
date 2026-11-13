package dev.wisteria.platform.game;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import dev.wisteria.render.FontFace;
import dev.wisteria.ui.Metrics;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jspecify.annotations.Nullable;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryUtil;

/**
 * The client's own text: glyphs are rasterised by stb_truetype at the size they will actually cover on screen
 * (GUI units x window GUI scale x transform scale), times {@link Metrics.Text#OVERSAMPLE}, into atlases sampled
 * with bilinear filtering. The game's font path rasterises once at a fixed size and samples with NEAREST, which
 * is what made scaled text look stepped.
 *
 * <p>Layout is resolution-independent: advances and kerning come from the font's design units, so measuring
 * text never depends on how it is later rasterised.
 */
final class TextRenderer {
	private static final int PAGE_SIZE = 1024;
	private static final int PADDING = 2;
	private static final int MIN_PIXELS = 4;
	private static final int MAX_PIXELS = 160;

	private final Map<FontFace, Face> faces = new EnumMap<>(FontFace.class);
	private final List<Page> pages = new ArrayList<>();
	private @Nullable GpuSampler sampler;

	/** Where a glyph sits in an atlas, in pixels relative to the pen at the baseline. */
	record Glyph(@Nullable Page page, float u0, float v0, float u1, float v1, int offsetX, int offsetY, int width, int height) {
	}

	/** One positioned glyph, in GUI units, ready to become a quad. */
	record Quad(Page page, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
	}

	/** A loaded typeface. Its font data must stay alive as long as stb holds the info struct. */
	static final class Face {
		final STBTTFontinfo info;
		final ByteBuffer data;
		final float unitsToEm;
		final float ascent;
		final float descent;
		final Long2ObjectOpenHashMap<Glyph> glyphs = new Long2ObjectOpenHashMap<>();
		final it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap indices = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();

		Face(ByteBuffer data) {
			this.data = data;
			this.info = STBTTFontinfo.malloc();
			if (!STBTruetype.stbtt_InitFont(this.info, data)) {
				throw new IllegalStateException("Not a TrueType font");
			}

			this.unitsToEm = STBTruetype.stbtt_ScaleForMappingEmToPixels(this.info, 1.0F);
			int[] ascent = new int[1];
			int[] descent = new int[1];
			int[] gap = new int[1];
			STBTruetype.stbtt_GetFontVMetrics(this.info, ascent, descent, gap);
			this.ascent = ascent[0] * this.unitsToEm;
			this.descent = -descent[0] * this.unitsToEm;
			this.indices.defaultReturnValue(-1);
		}

		int glyphIndex(int codepoint) {
			int index = this.indices.get(codepoint);
			if (index < 0) {
				index = STBTruetype.stbtt_FindGlyphIndex(this.info, codepoint);
				this.indices.put(codepoint, index);
			}

			return index;
		}

		/** Advance in em. */
		float advance(int glyph) {
			int[] advance = new int[1];
			int[] bearing = new int[1];
			STBTruetype.stbtt_GetGlyphHMetrics(this.info, glyph, advance, bearing);
			return advance[0] * this.unitsToEm;
		}

		/** Kerning in em between two glyphs. */
		float kern(int left, int right) {
			return STBTruetype.stbtt_GetGlyphKernAdvance(this.info, left, right) * this.unitsToEm;
		}

		/** Where the baseline goes inside a line box of 1.25 em: the font's own ascent and descent, centred. */
		float baseline() {
			return (Metrics.Text.LINE_HEIGHT - (this.ascent + this.descent)) / 2.0F + this.ascent;
		}
	}

	/** One atlas texture, packed in shelves. */
	static final class Page {
		final DynamicTexture texture;
		int shelfX;
		int shelfY;
		int shelfHeight;
		boolean dirty;

		Page(int index) {
			NativeImage image = new NativeImage(NativeImage.Format.RGBA, PAGE_SIZE, PAGE_SIZE, false);
			// White with zero alpha, not black: bilinear filtering blends neighbours, and black would darken glyph edges.
			image.fillRect(0, 0, PAGE_SIZE, PAGE_SIZE, 0x00FFFFFF);
			this.texture = new DynamicTexture(() -> "wisteria text " + index, image);
		}

		/** @return the top-left corner reserved for a w x h bitmap, or null if the page is full */
		int @Nullable [] reserve(int width, int height) {
			if (this.shelfX + width + PADDING > PAGE_SIZE) {
				this.shelfX = 0;
				this.shelfY += this.shelfHeight + PADDING;
				this.shelfHeight = 0;
			}

			if (this.shelfY + height + PADDING > PAGE_SIZE || width + PADDING > PAGE_SIZE) {
				return null;
			}

			int[] corner = {this.shelfX + PADDING, this.shelfY + PADDING};
			this.shelfX += width + PADDING;
			this.shelfHeight = Math.max(this.shelfHeight, height);
			return corner;
		}
	}

	GpuSampler sampler() {
		if (this.sampler == null) {
			this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		}

		return this.sampler;
	}

	Face face(FontFace face) {
		return this.faces.computeIfAbsent(face, TextRenderer::load);
	}

	private static Face load(FontFace face) {
		String file = switch (face) {
			case REGULAR -> "inter-regular.ttf";
			case SEMIBOLD -> "inter-semibold.ttf";
			case MONO -> "jetbrains-mono-regular.ttf";
		};
		try (InputStream input = TextRenderer.class.getResourceAsStream("/assets/wisteria/font/" + file)) {
			if (input == null) {
				throw new IllegalStateException("Missing font " + file);
			}

			byte[] bytes = input.readAllBytes();
			ByteBuffer data = MemoryUtil.memAlloc(bytes.length);
			data.put(bytes).flip();
			return new Face(data);
		} catch (IOException e) {
			throw new IllegalStateException("Could not read font " + file, e);
		}
	}

	/** Width in GUI units of the text at {@code size} (em height in GUI units). */
	float width(FontFace fontFace, float size, String text) {
		Face face = this.face(fontFace);
		float pen = 0.0F;
		int previous = -1;
		for (int i = 0; i < text.length(); ) {
			int codepoint = text.codePointAt(i);
			i += Character.charCount(codepoint);
			int glyph = face.glyphIndex(codepoint);
			if (previous >= 0) {
				pen += face.kern(previous, glyph);
			}

			pen += face.advance(glyph);
			previous = glyph;
		}

		return pen * size;
	}

	/**
	 * Lays out a line. {@code pixelsPerUnit} is how many screen pixels one GUI unit covers where the text is drawn.
	 *
	 * @param y top of the line box
	 */
	List<Quad> layout(FontFace fontFace, float size, String text, float x, float y, float pixelsPerUnit) {
		Face face = this.face(fontFace);
		int oversample = Metrics.Text.OVERSAMPLE;
		int pixels = Math.round(size * pixelsPerUnit * oversample);
		if (pixels > MAX_PIXELS) {
			pixels = MAX_PIXELS;
		}

		pixels = Math.max(MIN_PIXELS, pixels);
		// GUI units per rasterised pixel.
		float unit = size / pixels;
		// Snap the baseline to a whole screen pixel: horizontal strokes then land on pixel rows and stay sharp.
		float baseline = y + face.baseline() * size;
		baseline = Math.round(baseline * pixelsPerUnit) / pixelsPerUnit;

		List<Quad> quads = new ArrayList<>(text.length());
		float pen = x;
		int previous = -1;
		for (int i = 0; i < text.length(); ) {
			int codepoint = text.codePointAt(i);
			i += Character.charCount(codepoint);
			int glyphIndex = face.glyphIndex(codepoint);
			if (previous >= 0) {
				pen += face.kern(previous, glyphIndex) * size;
			}

			Glyph glyph = this.glyph(face, glyphIndex, pixels);
			if (glyph.page() != null) {
				float x0 = pen + glyph.offsetX() * unit;
				float y0 = baseline + glyph.offsetY() * unit;
				quads.add(new Quad(glyph.page(), x0, y0, x0 + glyph.width() * unit, y0 + glyph.height() * unit,
					glyph.u0(), glyph.v0(), glyph.u1(), glyph.v1()));
			}

			pen += face.advance(glyphIndex) * size;
			previous = glyphIndex;
		}

		for (Page page : this.pages) {
			if (page.dirty) {
				page.dirty = false;
				page.texture.upload();
			}
		}

		return quads;
	}

	private Glyph glyph(Face face, int glyphIndex, int pixels) {
		long key = (long)pixels << 32 | glyphIndex;
		Glyph cached = face.glyphs.get(key);
		if (cached != null) {
			return cached;
		}

		float scale = STBTruetype.stbtt_ScaleForMappingEmToPixels(face.info, pixels);
		int[] x0 = new int[1];
		int[] y0 = new int[1];
		int[] x1 = new int[1];
		int[] y1 = new int[1];
		STBTruetype.stbtt_GetGlyphBitmapBox(face.info, glyphIndex, scale, scale, x0, y0, x1, y1);
		int width = x1[0] - x0[0];
		int height = y1[0] - y0[0];
		Glyph glyph;
		if (width <= 0 || height <= 0) {
			glyph = new Glyph(null, 0.0F, 0.0F, 0.0F, 0.0F, 0, 0, 0, 0);
		} else {
			// One pixel of empty border on each side, so bilinear filtering fades to nothing at the quad's edge.
			int w = width + 2;
			int h = height + 2;
			Page page = null;
			int[] corner = null;
			for (Page candidate : this.pages) {
				corner = candidate.reserve(w, h);
				if (corner != null) {
					page = candidate;
					break;
				}
			}

			if (corner == null) {
				page = new Page(this.pages.size());
				this.pages.add(page);
				corner = page.reserve(w, h);
			}

			ByteBuffer bitmap = MemoryUtil.memCalloc(width * height);
			try {
				STBTruetype.stbtt_MakeGlyphBitmap(face.info, bitmap, width, height, width, scale, scale, glyphIndex);
				NativeImage image = page.texture.getPixels();
				for (int row = 0; row < height; row++) {
					for (int column = 0; column < width; column++) {
						int coverage = bitmap.get(row * width + column) & 0xFF;
						image.setPixel(corner[0] + 1 + column, corner[1] + 1 + row, coverage << 24 | 0xFFFFFF);
					}
				}
			} finally {
				MemoryUtil.memFree(bitmap);
			}

			page.dirty = true;
			float texel = 1.0F / PAGE_SIZE;
			glyph = new Glyph(page, corner[0] * texel, corner[1] * texel, (corner[0] + w) * texel, (corner[1] + h) * texel,
				x0[0] - 1, y0[0] - 1, w, h);
		}

		face.glyphs.put(key, glyph);
		return glyph;
	}
}
