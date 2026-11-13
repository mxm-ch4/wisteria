package dev.wisteria;

import dev.wisteria.render.FontFace;
import dev.wisteria.render.Renderer2D;
import java.util.ArrayList;
import java.util.List;

/** A renderer that draws nothing: it measures text predictably and remembers what text was asked for. */
public final class FakeRenderer implements Renderer2D {
	public final List<String> texts = new ArrayList<>();
	public int calls;
	private final float width;
	private final float height;
	private int scissorDepth;
	private int poseDepth;

	public FakeRenderer(float width, float height) {
		this.width = width;
		this.height = height;
	}

	/** True when every push has been popped: a frame that leaks state corrupts whatever is drawn after it. */
	public boolean balanced() {
		return this.scissorDepth == 0 && this.poseDepth == 0;
	}

	public boolean drew(String text) {
		return this.texts.contains(text);
	}

	@Override
	public float width() {
		return this.width;
	}

	@Override
	public float height() {
		return this.height;
	}

	@Override
	public void rect(float x, float y, float width, float height, int argb) {
		this.calls++;
	}

	@Override
	public void gradient(float x, float y, float width, float height, int topLeft, int topRight, int bottomLeft, int bottomRight) {
		this.calls++;
	}

	@Override
	public void roundedRect(float x, float y, float width, float height, float radius, int argb) {
		this.calls++;
	}

	@Override
	public void roundedGradient(float x, float y, float width, float height, float radius, int topLeft, int topRight, int bottomLeft, int bottomRight) {
		this.calls++;
	}

	@Override
	public void roundedOutline(float x, float y, float width, float height, float radius, float thickness, int argb) {
		this.calls++;
	}

	@Override
	public void shadow(float x, float y, float width, float height, float radius, float blur, int argb) {
		this.calls++;
	}

	@Override
	public void text(String text, float x, float y, int argb, boolean shadow) {
		this.texts.add(text);
	}

	@Override
	public float textWidth(String text) {
		return text.length() * 6.0F;
	}

	@Override
	public float lineHeight() {
		return 9.0F;
	}

	@Override
	public void text(FontFace face, float size, String text, float x, float y, int argb) {
		this.texts.add(text);
	}

	@Override
	public float textWidth(FontFace face, float size, String text) {
		return text.length() * size * 0.5F;
	}

	@Override
	public float lineHeight(FontFace face, float size) {
		return size * 1.25F;
	}

	@Override
	public void pushScissor(float x, float y, float width, float height) {
		this.scissorDepth++;
	}

	@Override
	public void popScissor() {
		this.scissorDepth--;
	}

	@Override
	public void push() {
		this.poseDepth++;
	}

	@Override
	public void pop() {
		this.poseDepth--;
	}

	@Override
	public void translate(float dx, float dy) {
	}

	@Override
	public void scale(float sx, float sy) {
	}

	@Override
	public void rotate(float radians) {
	}

	@Override
	public void blurBehind() {
	}
}
