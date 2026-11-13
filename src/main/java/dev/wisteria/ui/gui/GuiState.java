package dev.wisteria.ui.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.wisteria.module.Category;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;

/** Where the user left the ClickGUI panels, so they are found there next time. Saved in client.json. */
public final class GuiState {
	/** @param collapsed true when only the panel's header is shown */
	public record PanelState(float x, float y, boolean collapsed) {
	}

	private final Map<Category, PanelState> panels = new EnumMap<>(Category.class);
	private final CopyOnWriteArrayList<Runnable> changeListeners = new CopyOnWriteArrayList<>();

	public @Nullable PanelState panel(Category category) {
		return this.panels.get(category);
	}

	public void setPanel(Category category, PanelState state) {
		if (!state.equals(this.panels.put(category, state))) {
			for (Runnable listener : this.changeListeners) {
				listener.run();
			}
		}
	}

	public void onChange(Runnable listener) {
		this.changeListeners.add(listener);
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		this.panels.forEach((category, state) -> {
			JsonObject panel = new JsonObject();
			panel.addProperty("x", state.x());
			panel.addProperty("y", state.y());
			panel.addProperty("collapsed", state.collapsed());
			json.add(category.name().toLowerCase(Locale.ROOT), panel);
		});
		return json;
	}

	/** Replaces the state without notifying listeners; anything unreadable is skipped. */
	public void load(JsonObject json) {
		this.panels.clear();
		for (Category category : Category.values()) {
			JsonElement element = json.get(category.name().toLowerCase(Locale.ROOT));
			if (element == null || !element.isJsonObject()) {
				continue;
			}

			JsonObject panel = element.getAsJsonObject();
			try {
				float x = panel.get("x").getAsFloat();
				float y = panel.get("y").getAsFloat();
				boolean collapsed = panel.has("collapsed") && panel.get("collapsed").getAsBoolean();
				if (Float.isFinite(x) && Float.isFinite(y)) {
					this.panels.put(category, new PanelState(x, y, collapsed));
				}
			} catch (RuntimeException ignored) {
				// a hand-edited entry that is not two numbers: fall back to the default layout
			}
		}
	}
}
