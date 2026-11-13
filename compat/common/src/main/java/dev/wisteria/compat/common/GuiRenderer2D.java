package dev.wisteria.compat.common;

import dev.wisteria.compat.FontFace;
import dev.wisteria.compat.Renderer2D;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/** {@link Renderer2D} on top of the game's GUI extractor: every call adds elements to the frame's GUI state. */
final class GuiRenderer2D implements Renderer2D {
	private final CommonPlatform platform;
	private final GuiGraphicsExtractor graphics;
	private final Font font;
	private final ArrayDeque<ScreenRectangle> scissors = new ArrayDeque<>();
	private int poseDepth;
	private boolean blurred;

	GuiRenderer2D(CommonPlatform platform, GuiGraphicsExtractor graphics) {
		this.platform = platform;
		this.graphics = graphics;
		this.font = Minecraft.getInstance().font;
	}

	/** Undoes whatever a listener forgot to pop, so one sloppy caller cannot corrupt the rest of the frame. */
	void finish() {
		while (!this.scissors.isEmpty()) {
			this.popScissor();
		}

		while (this.poseDepth > 0) {
			this.pop();
		}
	}

	@Override
	public float width() {
		return this.graphics.guiWidth();
	}

	@Override
	public float height() {
		return this.graphics.guiHeight();
	}

	@Override
	public void rect(float x, float y, float width, float height, int argb) {
		this.gradient(x, y, width, height, argb, argb, argb, argb);
	}

	@Override
	public void gradient(float x, float y, float width, float height, int topLeft, int topRight, int bottomLeft, int bottomRight) {
		if (width <= 0.0F || height <= 0.0F) {
			return;
		}

		QuadGeometry geometry = QuadGeometry.plain(this.pose(), x, y, x + width, y + height, topLeft, bottomLeft, bottomRight, topRight, this.scissor());
		this.graphics.guiRenderState.addGuiElement(this.platform.plainQuad(geometry));
	}

	@Override
	public void roundedRect(float x, float y, float width, float height, float radius, int argb) {
		this.roundedGradient(x, y, width, height, radius, argb, argb, argb, argb);
	}

	@Override
	public void roundedGradient(float x, float y, float width, float height, float radius, int topLeft, int topRight, int bottomLeft, int bottomRight) {
		this.panel(x, y, width, height, radius, 0.0F, 0.0F, topLeft, topRight, bottomLeft, bottomRight);
	}

	@Override
	public void roundedOutline(float x, float y, float width, float height, float radius, float thickness, int argb) {
		if (thickness > 0.0F) {
			this.panel(x, y, width, height, radius, thickness, 0.0F, argb, argb, argb, argb);
		}
	}

	@Override
	public void shadow(float x, float y, float width, float height, float radius, float blur, int argb) {
		if (blur > 0.0F) {
			this.panel(x, y, width, height, radius, 0.0F, Math.min(blur, 127.0F), argb, argb, argb, argb);
		}
	}

	private void panel(float x, float y, float width, float height, float radius, float stroke, float feather,
		int topLeft, int topRight, int bottomLeft, int bottomRight) {
		if (width <= 0.0F || height <= 0.0F) {
			return;
		}

		float r = Math.max(0.0F, Math.min(radius, Math.min(width, height) / 2.0F));
		QuadGeometry geometry = QuadGeometry.panel(this.pose(), x, y, width, height, r, stroke, feather, topLeft, bottomLeft, bottomRight, topRight, this.scissor());
		this.graphics.guiRenderState.addGuiElement(this.platform.panelQuad(geometry));
	}

	@Override
	public void text(String text, float x, float y, int argb, boolean shadow) {
		// The game's text call takes ints; the fractional part goes into the transform.
		this.graphics.pose().pushMatrix();
		this.graphics.pose().translate(x, y);
		this.graphics.text(this.font, text, 0, 0, argb, shadow);
		this.graphics.pose().popMatrix();
	}

	@Override
	public float textWidth(String text) {
		return this.font.width(text);
	}

	@Override
	public float lineHeight() {
		return this.font.lineHeight;
	}

	@Override
	public void text(FontFace face, float size, String text, float x, float y, int argb) {
		// The font is rasterised at Fonts.BASE_SIZE (times its oversample); other sizes are a transform away.
		float scale = size / Fonts.BASE_SIZE;
		this.graphics.pose().pushMatrix();
		this.graphics.pose().translate(x, y + Fonts.topOffset(face, size));
		this.graphics.pose().scale(scale, scale);
		this.graphics.text(this.font, Fonts.styled(face, text), 0, 0, argb, false);
		this.graphics.pose().popMatrix();
	}

	@Override
	public float textWidth(FontFace face, float size, String text) {
		return this.font.width(Fonts.styled(face, text)) * size / Fonts.BASE_SIZE;
	}

	@Override
	public float lineHeight(FontFace face, float size) {
		return Fonts.lineHeight(size);
	}

	@Override
	public void pushScissor(float x, float y, float width, float height) {
		int left = (int)Math.floor(x);
		int top = (int)Math.floor(y);
		int right = (int)Math.ceil(x + width);
		int bottom = (int)Math.ceil(y + height);
		ScreenRectangle rectangle = new ScreenRectangle(left, top, right - left, bottom - top).transformAxisAligned(this.graphics.pose());
		ScreenRectangle parent = this.scissors.peek();
		if (parent != null) {
			ScreenRectangle intersection = rectangle.intersection(parent);
			rectangle = intersection != null ? intersection : CommonPlatform.emptyRectangle();
		}

		this.scissors.push(rectangle);
		// Text goes through the game's own scissor stack, so keep it in step with ours.
		this.graphics.enableScissor(left, top, right, bottom);
	}

	@Override
	public void popScissor() {
		if (!this.scissors.isEmpty()) {
			this.scissors.pop();
			this.graphics.disableScissor();
		}
	}

	@Override
	public void push() {
		this.graphics.pose().pushMatrix();
		this.poseDepth++;
	}

	@Override
	public void pop() {
		if (this.poseDepth > 0) {
			this.graphics.pose().popMatrix();
			this.poseDepth--;
		}
	}

	@Override
	public void translate(float dx, float dy) {
		this.graphics.pose().translate(dx, dy);
	}

	@Override
	public void scale(float sx, float sy) {
		this.graphics.pose().scale(sx, sy);
	}

	@Override
	public void blurBehind() {
		if (this.blurred) {
			return;
		}

		this.blurred = true;
		this.graphics.nextStratum();
		try {
			this.graphics.blurBeforeThisStratum();
		} catch (IllegalStateException alreadyBlurred) {
			// The game blurs once per frame and a screen background may have used it already.
		}
	}

	private Matrix3x2f pose() {
		return new Matrix3x2f(this.graphics.pose());
	}

	private @Nullable ScreenRectangle scissor() {
		return this.scissors.peek();
	}
}
