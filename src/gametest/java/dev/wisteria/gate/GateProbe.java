package dev.wisteria.gate;

import dev.wisteria.platform.ClientListener;
import dev.wisteria.render.FontFace;
import dev.wisteria.platform.InputBackend;
import dev.wisteria.platform.Key;
import dev.wisteria.platform.PacketAdapter;
import dev.wisteria.render.Renderer2D;
import dev.wisteria.render.Renderer3D;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Listens on every compat interface and draws a little of everything. It only uses what core can use,
 * so a green gate means the client's own code has what it needs on this version.
 */
final class GateProbe implements ClientListener, InputBackend.InputListener, PacketAdapter.PacketListener {
	static final int SURFACE = 0xE617131F;
	static final int TEXT = 0xFFECE8F5;
	static final int MUTED = 0xFF9A92AD;
	static final int PETAL = 0xFFE9D8FF;
	static final int WISTERIA = 0xFFB79CFF;
	static final int VIOLET = 0xFF8B6CFF;
	static final int DEEP = 0xFF5B3FD6;

	final AtomicInteger ticks = new AtomicInteger();
	final AtomicInteger frames2D = new AtomicInteger();
	final AtomicInteger frames3D = new AtomicInteger();
	final AtomicInteger sent = new AtomicInteger();
	final AtomicInteger received = new AtomicInteger();
	final AtomicInteger cancelled = new AtomicInteger();
	final Set<String> sentNames = ConcurrentHashMap.newKeySet();
	final Set<String> receivedNames = ConcurrentHashMap.newKeySet();
	final List<String> keys = new CopyOnWriteArrayList<>();

	/** While set, outgoing packets with this name are dropped. */
	volatile String dropOutgoing;
	/** Set to capture a point in front of the camera on the next frame and highlight it from then on. */
	volatile boolean captureTarget;
	private volatile double[] target;
	volatile String targetInfo = "none";
	/** GUI-scaled width of the last frame, to convert GUI coordinates to pixels. */
	volatile float guiWidth = 1.0F;
	/** Widths of "iiii" and "WWWW" in the regular, semibold and mono faces at 16 px. */
	volatile float[] fontWidths = new float[6];
	private final String version;

	/** The probe of this launch, for tests that want a clean frame (see {@link #hidden}). */
	static volatile GateProbe current;
	/** While set, the probe still counts frames but draws nothing over the HUD. */
	volatile boolean hidden;

	GateProbe(String version) {
		this.version = version;
		current = this;
	}

	@Override
	public void onTick() {
		this.ticks.incrementAndGet();
	}

	@Override
	public void onRender2D(Renderer2D renderer, float tickDelta) {
		this.frames2D.incrementAndGet();
		this.guiWidth = renderer.width();
		if (this.hidden) {
			return;
		}

		float x = 8.5F;
		float y = 8.5F;
		float width = 184.0F;
		float height = 66.0F;
		renderer.roundedRect(x, y, width, height, 8.0F, SURFACE);
		renderer.gradient(x + 8.0F, y + 9.0F, 3.0F, height - 18.0F, PETAL, PETAL, DEEP, DEEP);
		renderer.text("Wisteria gate  " + this.version, x + 18.0F, y + 9.0F, TEXT, false);
		renderer.text("ticks " + this.ticks.get(), x + 18.0F, y + 21.0F, MUTED, false);
		renderer.text("packets out " + this.sent.get() + " in " + this.received.get(), x + 18.0F, y + 32.0F, MUTED, false);
		// Horizontal gradient, clipped to a narrower region: per-corner colours and scissor in one bar.
		renderer.pushScissor(x + 18.0F, y + 46.0F, 120.0F, 10.0F);
		renderer.gradient(x + 18.0F, y + 46.0F, 150.0F, 10.0F, DEEP, PETAL, DEEP, PETAL);
		renderer.popScissor();
		renderer.roundedRect(x + width - 22.0F, y + 8.0F, 14.0F, 14.0F, 7.0F, VIOLET);

		// Design-system primitives: a glow, a gradient panel with an outline, and the three bundled fonts.
		float px = 8.5F;
		float py = 84.5F;
		renderer.shadow(px, py, 184.0F, 58.0F, 10.0F, 10.0F, 0x99B79CFF);
		renderer.roundedGradient(px, py, 184.0F, 58.0F, 10.0F, 0xFF211B2E, 0xFF2E2740, 0xFF17131F, 0xFF211B2E);
		renderer.roundedOutline(px, py, 184.0F, 58.0F, 10.0F, 1.0F, 0xFF8B6CFF);
		renderer.text(FontFace.SEMIBOLD, 13.0F, "Inter SemiBold 13", px + 10.0F, py + 6.0F, TEXT);
		renderer.text(FontFace.REGULAR, 10.0F, "Inter Regular 10 — Привет", px + 10.0F, py + 24.0F, MUTED);
		renderer.text(FontFace.MONO, 10.0F, "mono 0123456789", px + 10.0F, py + 38.0F, WISTERIA);

		this.fontWidths = new float[] {
			renderer.textWidth(FontFace.REGULAR, 16.0F, "iiii"), renderer.textWidth(FontFace.REGULAR, 16.0F, "WWWW"),
			renderer.textWidth(FontFace.SEMIBOLD, 16.0F, "iiii"), renderer.textWidth(FontFace.SEMIBOLD, 16.0F, "WWWW"),
			renderer.textWidth(FontFace.MONO, 16.0F, "iiii"), renderer.textWidth(FontFace.MONO, 16.0F, "WWWW")
		};

		// A scaled copy through the transform stack.
		String label = "scaled x1.5";
		renderer.push();
		renderer.translate(renderer.width() - renderer.textWidth(label) * 1.5F - 8.0F, 8.0F);
		renderer.scale(1.5F, 1.5F);
		renderer.text(label, 0.0F, 0.0F, WISTERIA, true);
		renderer.pop();
	}

	@Override
	public void onRender3D(Renderer3D renderer, float tickDelta) {
		this.frames3D.incrementAndGet();
		if (this.captureTarget) {
			this.captureTarget = false;
			this.target = new double[] {
				Math.floor(renderer.cameraX() + renderer.cameraForwardX() * 5.0),
				Math.floor(renderer.cameraY() + renderer.cameraForwardY() * 5.0),
				Math.floor(renderer.cameraZ() + renderer.cameraForwardZ() * 5.0)
			};
			this.targetInfo = String.format("camera=(%.2f, %.2f, %.2f) forward=(%.2f, %.2f, %.2f) box=(%.0f, %.0f, %.0f)",
				renderer.cameraX(), renderer.cameraY(), renderer.cameraZ(),
				renderer.cameraForwardX(), renderer.cameraForwardY(), renderer.cameraForwardZ(),
				this.target[0], this.target[1], this.target[2]);
		}

		double[] box = this.target;
		if (box == null) {
			return;
		}

		renderer.box(box[0], box[1], box[2], box[0] + 1.0, box[1] + 1.0, box[2] + 1.0, WISTERIA, 3.0F, 0x558B6CFF, true);
		renderer.line(
			renderer.cameraX() + renderer.cameraForwardX() * 0.6,
			renderer.cameraY() + renderer.cameraForwardY() * 0.6 - 0.12,
			renderer.cameraZ() + renderer.cameraForwardZ() * 0.6,
			box[0] + 0.5, box[1] + 0.5, box[2] + 0.5, PETAL, 2.0F, true
		);
	}

	void clearTarget() {
		this.target = null;
	}

	@Override
	public void onKey(Key key, InputBackend.Action action, boolean shift, boolean control, boolean alt, boolean inScreen) {
		this.keys.add(key + ":" + action);
	}

	@Override
	public boolean onSend(PacketAdapter.PacketView packet) {
		this.sent.incrementAndGet();
		this.sentNames.add(packet.name());
		String drop = this.dropOutgoing;
		if (drop != null && drop.equals(packet.name())) {
			this.cancelled.incrementAndGet();
			return true;
		}

		return false;
	}

	@Override
	public boolean onReceive(PacketAdapter.PacketView packet) {
		this.received.incrementAndGet();
		this.receivedNames.add(packet.name());
		return false;
	}
}
