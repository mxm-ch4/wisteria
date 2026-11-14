package dev.wisteria.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.wisteria.Client;
import dev.wisteria.event.Events;
import dev.wisteria.module.Module;
import dev.wisteria.render.Renderer2D;
import dev.wisteria.ui.Animation;
import dev.wisteria.ui.Easing;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;

/**
 * Lays out and draws the HUD: every {@link HudElement} that is on (or still fading out), at its saved placement.
 * Owns the toast queue other parts of the client post notifications to. Placements live in client.json, like
 * the ClickGUI layout: they belong to the screen, not to a profile. Client thread.
 */
public final class HudManager {
	private final Client client;
	private final Map<String, HudLayout.Placement> placements = new HashMap<>();
	private final Map<HudElement, Animation> visibility = new IdentityHashMap<>();
	private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
	private final Toasts toasts = new Toasts(System::nanoTime);
	private @Nullable Set<String> lastEnabled;
	private boolean editing;
	private List<Drawn> lastDrawn = List.of();

	public HudManager(Client client) {
		this.client = client;
		client.bus().subscribe(Events.Render2D.class, event -> {
			if (!this.editing) {
				this.render(event.renderer(), false);
			}
		});
		client.bus().subscribe(Events.Tick.class, event -> this.watchModules());
		client.bus().subscribe(Events.Death.class, event -> this.notify("You died",
			String.format(Locale.ROOT, "%.0f, %.0f, %.0f", event.position().x, event.position().y, event.position().z), Toasts.Kind.WARNING));
	}

	public List<HudElement> elements() {
		List<HudElement> elements = new ArrayList<>();
		for (Module module : this.client.modules().all()) {
			if (module instanceof HudElement element) {
				elements.add(element);
			}
		}

		return elements;
	}

	public @Nullable HudElement element(String id) {
		return this.client.modules().get(id) instanceof HudElement element ? element : null;
	}

	// ---- placements ----

	public HudLayout.Placement placement(HudElement element) {
		return this.placements.getOrDefault(element.id(), element.defaultPlacement());
	}

	public void setPlacement(HudElement element, HudLayout.Placement placement) {
		if (!placement.equals(this.placement(element))) {
			this.placements.put(element.id(), placement);
			this.changed();
		}
	}

	public boolean isMoved(HudElement element) {
		return !this.placement(element).equals(element.defaultPlacement());
	}

	public void reset(HudElement element) {
		if (this.placements.remove(element.id()) != null) {
			this.changed();
		}
	}

	public void resetAll() {
		if (!this.placements.isEmpty()) {
			this.placements.clear();
			this.changed();
		}
	}

	// ---- drawing ----

	/** An element as last drawn: what the editor hit-tests and snaps against. */
	public record Drawn(HudElement element, HudLayout.Rect rect) {
	}

	/**
	 * Draws every visible element.
	 *
	 * @param preview true in the editor: sample content where there is none, no fading
	 * @return where each element went
	 */
	private java.util.function.BooleanSupplier hidden = () -> false;

	/** While {@code when} says so, nothing is drawn, at once and without fading (the panic button). */
	public void hideWhile(java.util.function.BooleanSupplier when) {
		this.hidden = when;
		this.toasts.muteWhile(when);
	}

	public List<Drawn> render(Renderer2D renderer, boolean preview) {
		if (!preview && this.hidden.getAsBoolean()) {
			this.lastDrawn = List.of();
			this.visibility.clear();
			return List.of();
		}

		List<Drawn> drawn = new ArrayList<>();
		for (HudElement element : this.elements()) {
			Animation shown = this.visibility.computeIfAbsent(element, key -> new Animation(key.isEnabled() ? 1.0F : 0.0F, Animation.NORMAL, Easing.OUT_CUBIC));
			shown.animateTo(element.isEnabled());
			float alpha = preview ? (element.isEnabled() ? 1.0F : 0.0F) : shown.value();
			if (alpha <= 0.01F) {
				continue;
			}

			HudLayout.Placement placement = this.placement(element);
			HudContext context = new HudContext(renderer, this.client.themes().current(), alpha, preview, placement.anchor());
			float[] size = element.measureFor(context);
			if (size == null || size[0] <= 0.0F || size[1] <= 0.0F) {
				continue;
			}

			float scale = this.globalScale() * element.scale();
			HudLayout.Rect rect = HudLayout.place(placement, size[0] * scale, size[1] * scale, renderer.width(), renderer.height());
			renderer.push();
			// Fades in sliding a few pixels from the edge it is pinned to.
			float slide = (1.0F - alpha) * 6.0F;
			renderer.translate(rect.x() + (placement.anchor().right() ? slide : placement.anchor().fx() == 0.0F ? -slide : 0.0F),
				rect.y() + (placement.anchor().bottom() ? slide : placement.anchor().fy() == 0.0F ? -slide : 0.0F));
			renderer.scale(scale, scale);
			element.drawFor(context, size[0], size[1]);
			renderer.pop();
			drawn.add(new Drawn(element, rect));
		}

		this.lastDrawn = List.copyOf(drawn);
		return drawn;
	}

	/** What the last frame drew, and where; for diagnostics and tests. */
	public List<Drawn> lastDrawn() {
		return this.lastDrawn;
	}

	/** The HUD-wide size, the HUD Editor's "HUD Scale" setting; 1 if there is no such module. */
	public float globalScale() {
		Module editor = this.client.modules().get("hudeditor");
		Object value = editor != null && editor.setting("hud-scale") != null ? editor.setting("hud-scale").get() : null;
		return value instanceof Float f ? f : 1.0F;
	}

	/** While the editor is open it draws the HUD itself. */
	public void setEditing(boolean editing) {
		this.editing = editing;
	}

	public boolean isEditing() {
		return this.editing;
	}

	// ---- notifications ----

	public Toasts toasts() {
		return this.toasts;
	}

	/** Shows a toast, if the Notifications element is on. */
	public void notify(String title, String text, Toasts.Kind kind) {
		this.toasts.push(title, text, kind);
	}

	// A toast for each module switched on or off, however it happened (key, command, GUI).
	private void watchModules() {
		Set<String> enabled = new LinkedHashSet<>();
		for (Module module : this.client.modules().all()) {
			if (module.isEnabled() && module.savesEnabled() && !(module instanceof HudElement)) {
				enabled.add(module.name());
			}
		}

		Set<String> before = this.lastEnabled;
		this.lastEnabled = enabled;
		if (before == null) {
			return;
		}

		for (String name : switchedOn(before, enabled)) {
			this.notify(name, "Enabled", Toasts.Kind.ON);
		}

		for (String name : switchedOn(enabled, before)) {
			this.notify(name, "Disabled", Toasts.Kind.OFF);
		}
	}

	/** Names in {@code after} that were not in {@code before}. */
	static List<String> switchedOn(Set<String> before, Set<String> after) {
		return after.stream().filter(name -> !before.contains(name)).toList();
	}

	// ---- persistence ----

	public void onChange(Runnable listener) {
		this.changeListeners.add(listener);
	}

	private void changed() {
		this.changeListeners.forEach(Runnable::run);
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		this.placements.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
			JsonObject placement = new JsonObject();
			placement.addProperty("anchor", entry.getValue().anchor().id());
			placement.addProperty("x", entry.getValue().dx());
			placement.addProperty("y", entry.getValue().dy());
			json.add(entry.getKey(), placement);
		});
		return json;
	}

	/** Replaces the placements without notifying; entries that cannot be read are skipped. */
	public void load(JsonObject json) {
		this.placements.clear();
		for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
			try {
				JsonObject placement = entry.getValue().getAsJsonObject();
				Anchor anchor = Anchor.byId(placement.get("anchor").getAsString());
				float dx = placement.get("x").getAsFloat();
				float dy = placement.get("y").getAsFloat();
				if (anchor != null && Float.isFinite(dx) && Float.isFinite(dy)) {
					this.placements.put(entry.getKey(), new HudLayout.Placement(anchor, dx, dy));
				}
			} catch (RuntimeException ignored) {
				// a hand-edited entry that makes no sense: that element goes back to its default place
			}
		}
	}
}
