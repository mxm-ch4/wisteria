package dev.wisteria.ui;

import dev.wisteria.Client;
import dev.wisteria.render.Renderer2D;
import dev.wisteria.platform.ScreenBackend;
import dev.wisteria.ui.Metrics.Radius;
import dev.wisteria.ui.Metrics.Space;
import dev.wisteria.ui.Metrics.Stroke;
import dev.wisteria.ui.Metrics.Type;
import dev.wisteria.ui.gui.Controls;
import dev.wisteria.ui.gui.Ui;
import java.util.Locale;

/**
 * The design system on one screen: every colour role, the type scale, spacing and radii, control states and
 * the motion curves, all drawn with the active theme. Open with the "design" command. It is a reference and
 * a regression check, not part of the game UI: if something here looks wrong, it is wrong everywhere.
 */
public final class DesignScreen implements ScreenBackend.ClientScreen {
	private static final float WIDTH = 408.0F;
	private static final float HEIGHT = 222.0F;
	private static final long CYCLE_NANOS = 1_600_000_000L;

	private final Client client;
	private final Animation open = new Animation(0.0F, Animation.SLOW, Easing.OUT_QUINT);
	private final Animation hover = new Animation(0.0F, Animation.FAST, Easing.OUT_CUBIC);
	private final Animation toggle = new Animation(0.0F, Animation.NORMAL, Easing.OUT_BACK);
	private final Animation slider = new Animation(0.25F, Animation.FAST, Easing.OUT_CUBIC);
	private final Animation primaryLift = new Animation(0.0F, Animation.FAST, Easing.OUT_CUBIC);
	private final long openedAt = System.nanoTime();
	private final Controls controls = new Controls();
	private boolean toggleOn;
	private float sliderValue = 0.25F;
	private boolean dragging;
	private int primaryClicks;
	private int secondaryClicks;
	private float sliderX;
	private float sliderWidth = 116.0F;

	public DesignScreen(Client client) {
		this.client = client;
		this.open.animateTo(1.0F);
	}

	@Override
	public boolean mouseDown(float x, float y, ScreenBackend.MouseButton button) {
		this.controls.press(x, y);
		Controls.Control control = this.controls.at(x, y);
		if (button != ScreenBackend.MouseButton.LEFT || control == null || !control.enabled()) {
			return control != null;
		}

		switch (control.id()) {
			case "design:primary" -> this.primaryClicks++;
			case "design:secondary" -> this.secondaryClicks++;
			case "design:toggle" -> this.toggleOn = !this.toggleOn;
			case "design:slider" -> {
				this.dragging = true;
				this.sliderValue = Math.max(0.0F, Math.min(1.0F, (x - this.sliderX) / this.sliderWidth));
			}
			default -> {
			}
		}

		return true;
	}

	@Override
	public void mouseUp(float x, float y, ScreenBackend.MouseButton button) {
		this.controls.release();
		this.dragging = false;
	}

	/** The controls of the last frame: for the click-everything tests. */
	public Controls controls() {
		return this.controls;
	}

	@Override
	public String title() {
		return "Wisteria design system";
	}

	@Override
	public void render(Renderer2D renderer, float mouseX, float mouseY, float tickDelta) {
		Theme theme = this.client.themes().current();
		float appear = this.open.value();
		float x = Math.round((renderer.width() - WIDTH) / 2.0F);
		float y = Math.round((renderer.height() - HEIGHT) / 2.0F) + (1.0F - appear) * 10.0F;

		// Only the motion curves run on their own; the controls respond to the mouse.
		long elapsed = System.nanoTime() - this.openedAt;
		float loop = Math.min(1.0F, (float)(elapsed % CYCLE_NANOS) / (CYCLE_NANOS * 0.6F));
		this.controls.begin(mouseX, mouseY);
		if (this.dragging) {
			this.sliderValue = Math.max(0.0F, Math.min(1.0F, (mouseX - this.sliderX) / this.sliderWidth));
		}

		Painter paint = new Painter(renderer, appear);
		paint.shadow(x, y + 4.0F, WIDTH, HEIGHT, Radius.L, 18.0F, Colors.withAlpha(theme.shadow(), 150));
		paint.rounded(x, y, WIDTH, HEIGHT, Radius.L, Colors.withAlpha(theme.surface(), 242));
		paint.outline(x, y, WIDTH, HEIGHT, Radius.L, Stroke.HAIRLINE, theme.outline());

		// Header
		paint.text(Type.TITLE, "Design system", x + Space.M, y + Space.S, theme.text());
		String meta = theme.name().toLowerCase(Locale.ROOT) + "  ·  minecraft " + this.client.platform().minecraftVersion();
		paint.text(Type.MONO, meta, x + WIDTH - Space.M - Type.MONO.width(renderer, meta), y + Space.S + 3.0F, theme.textMuted());
		paint.rect(x + Space.M, y + 30.0F, WIDTH - 2.0F * Space.M, Stroke.HAIRLINE, theme.outline());

		float top = y + 38.0F;
		this.palette(paint, theme, x + Space.M, top);
		this.type(paint, theme, x + 148.0F, top);
		this.controls(paint, renderer, theme, x + 280.0F, top, mouseX, mouseY, loop);
		this.controls.end();
	}

	private void palette(Painter paint, Theme theme, float x, float y) {
		paint.text(Type.LABEL, "Colour", x, y, theme.text());
		float row = y + 16.0F;
		row = this.swatches(paint, theme, "neutral", x, row, theme.background(), theme.surface(), theme.raised(), theme.outline());
		row = this.swatches(paint, theme, "text", x, row, theme.text(), theme.textMuted(), theme.textDisabled(), theme.onAccent());
		row = this.swatches(paint, theme, "accent", x, row, theme.accent(), theme.accentHover(), theme.accentPressed());
		row = this.swatches(paint, theme, "signal", x, row, theme.success(), theme.warning(), theme.danger());

		paint.text(Type.CAPTION, "gradient", x, row + 4.0F, theme.textMuted());
		paint.gradient(x, row + 16.0F, 124.0F, 10.0F, Radius.pill(10.0F), theme.gradientStart(), theme.gradientEnd());

		paint.text(Type.CAPTION, "glow and shadow", x, row + 34.0F, theme.textMuted());
		paint.shadow(x + 4.0F, row + 50.0F, 52.0F, 18.0F, Radius.M, 7.0F, Colors.withAlpha(theme.accent(), 170));
		paint.rounded(x + 4.0F, row + 50.0F, 52.0F, 18.0F, Radius.M, theme.raised());
		paint.shadow(x + 68.0F, row + 52.0F, 52.0F, 18.0F, Radius.M, 6.0F, Colors.withAlpha(theme.shadow(), 200));
		paint.rounded(x + 68.0F, row + 50.0F, 52.0F, 18.0F, Radius.M, theme.raised());
	}

	private float swatches(Painter paint, Theme theme, String label, float x, float y, int... colours) {
		paint.text(Type.CAPTION, label, x, y + 2.0F, theme.textMuted());
		float sx = x + 40.0F;
		for (int colour : colours) {
			paint.rounded(sx, y, 18.0F, 12.0F, Radius.S, colour);
			paint.outline(sx, y, 18.0F, 12.0F, Radius.S, Stroke.HAIRLINE, Colors.withAlpha(theme.text(), 40));
			sx += 18.0F + Space.XS;
		}

		return y + 12.0F + Space.XS;
	}

	private void type(Painter paint, Theme theme, float x, float y) {
		paint.text(Type.LABEL, "Type", x, y, theme.text());
		float line = y + 16.0F;
		for (Type style : Type.values()) {
			String sample = switch (style) {
				case TITLE -> "Title 13";
				case LABEL -> "Label 10 · Модуль";
				case BODY -> "Body 10 · Описание";
				case CAPTION -> "Caption 8 · hint";
				case MONO -> "X 1024.5  Y 64.0";
			};
			paint.text(style, sample, x, line, style == Type.CAPTION ? theme.textMuted() : theme.text());
			line += Math.round(style.size() * 1.25F) + 2.0F;
		}

		paint.text(Type.CAPTION, "spacing 4 · 8 · 12 · 16", x, line + 4.0F, theme.textMuted());
		float sx = x;
		for (float step : new float[] {Space.XS, Space.S, Space.M, Space.L}) {
			paint.rounded(sx, line + 16.0F + (Space.L - step), step, step, 1.5F, Colors.withAlpha(theme.accent(), 200));
			sx += step + Space.S;
		}

		paint.text(Type.CAPTION, "radius 3 · 6 · 10", x, line + 40.0F, theme.textMuted());
		sx = x;
		for (float radius : new float[] {Radius.S, Radius.M, Radius.L}) {
			paint.rounded(sx, line + 52.0F, 30.0F, 20.0F, radius, theme.raised());
			paint.outline(sx, line + 52.0F, 30.0F, 20.0F, radius, Stroke.HAIRLINE, theme.outline());
			sx += 30.0F + Space.S;
		}
	}

	private void controls(Painter paint, Renderer2D renderer, Theme theme, float x, float y, float mouseX, float mouseY, float loop) {
		paint.text(Type.LABEL, "Controls", x, y, theme.text());

		// Buttons: primary and secondary count clicks; the third is disabled and must ignore them.
		float by = y + 16.0F;
		Controls.State primary = this.controls.register("design:primary", Controls.Kind.BUTTON, x, by, 46.0F, 16.0F,
			() -> Integer.toString(this.primaryClicks));
		Controls.State secondary = this.controls.register("design:secondary", Controls.Kind.BUTTON, x + 50.0F, by, 34.0F, 16.0F,
			() -> Integer.toString(this.secondaryClicks));
		Controls.State disabled = this.controls.register("design:disabled", Controls.Kind.BUTTON, x + 88.0F, by, 28.0F, 16.0F, false,
			() -> "never", null);
		this.primaryLift.animateTo(primary.hovered() ? 1.0F : 0.0F);
		float lift = this.primaryLift.value();
		paint.shadow(x, by, 46.0F, 16.0F, Radius.M, 6.0F, Colors.withAlpha(theme.accent(), Math.round(70.0F + 110.0F * lift)));
		int primaryFill = primary.pressed() ? theme.accentPressed() : Colors.mix(theme.accent(), theme.accentHover(), lift);
		paint.rounded(x, by, 46.0F, 16.0F, Radius.M, primaryFill);
		this.centred(paint, renderer, Type.LABEL, "Primary", x, by, 46.0F, 16.0F, theme.onAccent());

		paint.rounded(x + 50.0F, by, 34.0F, 16.0F, Radius.M, Ui.stateColor(theme.raised(), secondary));
		paint.outline(x + 50.0F, by, 34.0F, 16.0F, Radius.M, Stroke.HAIRLINE, secondary.hovered() ? theme.textMuted() : theme.outline());
		this.centred(paint, renderer, Type.LABEL, "Rest", x + 50.0F, by, 34.0F, 16.0F, theme.text());

		paint.rounded(x + 88.0F, by, 28.0F, 16.0F, Radius.M, Ui.stateColor(theme.raised(), disabled));
		this.centred(paint, renderer, Type.LABEL, "Off", x + 88.0F, by, 28.0F, 16.0F, theme.textDisabled());

		// Toggle: the knob overshoots slightly (OUT_BACK), the track cross-fades.
		float ty = by + 26.0F;
		Controls.State toggleState = this.controls.register("design:toggle", Controls.Kind.TOGGLE, x, ty - 2.0F, 116.0F, 16.0F,
			() -> this.toggleOn ? "on" : "off");
		this.toggle.animateTo(this.toggleOn);
		float on = this.toggle.value();
		float settled = Math.max(0.0F, Math.min(1.0F, on));
		paint.text(Type.BODY, "Toggle", x, ty, toggleState.hovered() ? theme.text() : theme.textMuted());
		paint.rounded(x + 92.0F, ty, 24.0F, 12.0F, Radius.pill(12.0F), Ui.stateColor(Colors.mix(theme.raised(), theme.accent(), settled), toggleState));
		paint.outline(x + 92.0F, ty, 24.0F, 12.0F, Radius.pill(12.0F), Stroke.HAIRLINE, Colors.mix(theme.outline(), theme.accent(), settled));
		float knob = toggleState.pressed() ? 7.0F : 8.0F;
		paint.rounded(x + 94.0F + (8.0F - knob) / 2.0F + on * 12.0F, ty + 2.0F + (8.0F - knob) / 2.0F, knob, knob, knob / 2.0F,
			Colors.mix(theme.textMuted(), theme.onAccent(), settled));

		// Slider: click or drag anywhere on the track.
		float sy = ty + 22.0F;
		this.sliderX = x;
		Controls.State sliderState = this.controls.register("design:slider", Controls.Kind.SLIDER, x, sy + 10.0F, 116.0F, 14.0F,
			() -> String.format(Locale.ROOT, "%.3f", this.sliderValue));
		if (this.dragging) {
			this.slider.snapTo(this.sliderValue);
		} else {
			this.slider.animateTo(this.sliderValue);
		}

		float value = this.slider.value();
		String number = String.format(Locale.ROOT, "%.1f", 8.0F + value * 248.0F);
		paint.text(Type.BODY, "Range", x, sy, sliderState.hovered() || this.dragging ? theme.text() : theme.textMuted());
		paint.text(Type.MONO, number, x + 116.0F - Type.MONO.width(renderer, number), sy + 1.0F, theme.textMuted());
		paint.rounded(x, sy + 16.0F, 116.0F, 4.0F, 2.0F, theme.raised());
		paint.gradient(x, sy + 16.0F, 116.0F * value, 4.0F, 2.0F, theme.accentPressed(), theme.accent());
		if (sliderState.hovered() || this.dragging) {
			paint.shadow(x + 116.0F * value - 4.0F, sy + 14.0F, 8.0F, 8.0F, 4.0F, this.dragging ? 6.0F : 4.0F, Colors.withAlpha(theme.accent(), 160));
		}

		paint.rounded(x + 116.0F * value - 4.0F, sy + 14.0F, 8.0F, 8.0F, 4.0F, theme.text());

		// Motion: the same 0..1 progress through three curves.
		float my = sy + 34.0F;
		paint.text(Type.LABEL, "Motion", x, my, theme.text());
		Easing[] curves = {Easing.LINEAR, Easing.OUT_CUBIC, Easing.OUT_BACK};
		for (int i = 0; i < curves.length; i++) {
			float ly = my + 16.0F + i * 15.0F;
			paint.text(Type.CAPTION, curves[i].name().toLowerCase(Locale.ROOT).replace('_', ' '), x, ly, theme.textMuted());
			paint.rounded(x + 50.0F, ly + 4.0F, 66.0F, 2.0F, 1.0F, theme.raised());
			paint.rounded(x + 50.0F + curves[i].apply(loop) * 60.0F, ly + 2.0F, 6.0F, 6.0F, 3.0F, theme.accent());
		}
	}

	private void centred(Painter paint, Renderer2D renderer, Type style, String text, float x, float y, float width, float height, int argb) {
		float textWidth = style.width(renderer, text);
		paint.text(style, text, x + (width - textWidth) / 2.0F, y + (height - style.size() * 1.25F) / 2.0F, argb);
	}

	/** Draws through the renderer with every colour faded by the screen's opening animation. */
	private record Painter(Renderer2D renderer, float opacity) {
		void rect(float x, float y, float width, float height, int argb) {
			this.renderer.rect(x, y, width, height, Colors.fade(argb, this.opacity));
		}

		void rounded(float x, float y, float width, float height, float radius, int argb) {
			this.renderer.roundedRect(x, y, width, height, radius, Colors.fade(argb, this.opacity));
		}

		void gradient(float x, float y, float width, float height, float radius, int left, int right) {
			int from = Colors.fade(left, this.opacity);
			int to = Colors.fade(right, this.opacity);
			this.renderer.roundedGradient(x, y, width, height, radius, from, to, from, to);
		}

		void outline(float x, float y, float width, float height, float radius, float thickness, int argb) {
			this.renderer.roundedOutline(x, y, width, height, radius, thickness, Colors.fade(argb, this.opacity));
		}

		void shadow(float x, float y, float width, float height, float radius, float blur, int argb) {
			this.renderer.shadow(x, y, width, height, radius, blur, Colors.fade(argb, this.opacity));
		}

		void text(Type style, String text, float x, float y, int argb) {
			// Fully transparent text is drawn opaque by the game, so skip it instead.
			if (this.opacity > 0.03F) {
				style.draw(this.renderer, text, x, y, Colors.fade(argb, this.opacity));
			}
		}
	}
}
