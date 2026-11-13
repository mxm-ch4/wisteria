package dev.wisteria.compat.common;

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
		float r = Math.min(radius, Math.min(width, height) / 2.0F);
		if (r <= 0.01F) {
			this.rect(x, y, width, height, argb);
			return;
		}

		this.rect(x + r, y, width - 2.0F * r, height, argb);
		this.rect(x, y + r, r, height - 2.0F * r, argb);
		this.rect(x + width - r, y + r, r, height - 2.0F * r, argb);
		this.cornerQuad(x + r, y + r, -r, -r, argb);
		this.cornerQuad(x + width - r, y + r, r, -r, argb);
		this.cornerQuad(x + r, y + height - r, -r, r, argb);
		this.cornerQuad(x + width - r, y + height - r, r, r, argb);
	}

	private void cornerQuad(float centerX, float centerY, float dx, float dy, int argb) {
		QuadGeometry geometry = QuadGeometry.corner(this.pose(), centerX, centerY, dx, dy, argb, this.scissor());
		this.graphics.guiRenderState.addGuiElement(this.platform.cornerQuad(geometry));
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
