package dev.wisteria.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;

/**
 * The active theme and the presets to choose from. Changing it takes effect on the next frame: nothing
 * caches colours.
 */
public final class ThemeManager {
	public static final String DEFAULT = "wisteria";

	private final Map<String, Theme> presets = new LinkedHashMap<>();
	private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
	private volatile Theme theme;
	private volatile @Nullable Integer customAccent;

	public ThemeManager() {
		this.add(Theme.of(DEFAULT, "Wisteria", Theme.Base.DARK, 0xFFB79CFF));
		this.add(Theme.of("sakura", "Sakura", Theme.Base.DARK, 0xFFFF9EC4));
		this.add(Theme.of("mint", "Mint", Theme.Base.DARK, 0xFF7FE3BC));
		this.add(Theme.of("ember", "Ember", Theme.Base.DARK, 0xFFFFB07A));
		this.add(Theme.of("frost", "Frost", Theme.Base.DARK, 0xFF8CC8FF));
		this.theme = this.presets.get(DEFAULT);
	}

	private void add(Theme preset) {
		this.presets.put(preset.id(), preset);
	}

	public Theme current() {
		return this.theme;
	}

	/** @throws IllegalArgumentException if there is no such preset */
	public Theme preset(String id) {
		Theme preset = this.presets.get(id.toLowerCase(Locale.ROOT));
		if (preset == null) {
			throw new IllegalArgumentException("No theme called \"" + id + "\"");
		}

		return preset;
	}

	public List<String> presetIds() {
		return List.copyOf(this.presets.keySet());
	}

	/** The preset the current theme is based on. */
	public String presetId() {
		return this.theme.id();
	}

	/** The accent override, if one is set on top of the preset. */
	public @Nullable Integer customAccent() {
		return this.customAccent;
	}

	/**
	 * Switches to a preset and drops any accent override.
	 *
	 * @throws IllegalArgumentException if there is no such preset
	 */
	public void usePreset(String id) {
		Theme preset = this.presets.get(id.toLowerCase(Locale.ROOT));
		if (preset == null) {
			throw new IllegalArgumentException("No theme called \"" + id + "\"; there are: " + String.join(", ", this.presets.keySet()));
		}

		this.customAccent = null;
		this.theme = preset;
		this.changed();
	}

	/** Keeps the current preset's neutrals and replaces its accent. */
	public void useAccent(int argb) {
		Theme preset = this.presets.get(this.theme.id());
		Theme.Base base = preset != null && !preset.dark() ? Theme.Base.LIGHT : Theme.Base.DARK;
		this.customAccent = Colors.withAlpha(argb, 255);
		this.theme = Theme.of(this.theme.id(), this.theme.name(), base, argb);
		this.changed();
	}

	/** Restores a saved choice without notifying listeners; unknown presets fall back to the default. */
	public void load(@Nullable String presetId, @Nullable Integer accent) {
		Theme preset = presetId != null ? this.presets.get(presetId.toLowerCase(Locale.ROOT)) : null;
		this.theme = preset != null ? preset : this.presets.get(DEFAULT);
		this.customAccent = null;
		if (accent != null) {
			this.customAccent = Colors.withAlpha(accent, 255);
			this.theme = Theme.of(this.theme.id(), this.theme.name(), this.theme.dark() ? Theme.Base.DARK : Theme.Base.LIGHT, accent);
		}
	}

	public void onChange(Runnable listener) {
		this.changeListeners.add(listener);
	}

	private void changed() {
		for (Runnable listener : this.changeListeners) {
			listener.run();
		}
	}
}
