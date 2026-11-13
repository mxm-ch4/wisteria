package dev.wisteria.modules.client;

import dev.wisteria.setting.ChoiceSetting;
import dev.wisteria.setting.InputSetting;
import dev.wisteria.setting.ListSetting;

/**
 * Profiles: each has its own module settings, proxy choice and data folder (.minecraft/wisteria/profiles/&lt;name&gt;/),
 * so nothing found or set in one shows up in another.
 */
public final class ProfilesSection extends ClientSection {
	private final ChoiceSetting active = this.add(new ChoiceSetting("Active", "The profile in use; switching saves this one first", "",
		() -> this.client().config().profiles(), false));
	private final ListSetting profiles = this.add(new ListSetting("Profiles", "New profiles start empty: default settings, no data, no proxy",
		"new profile name", () -> this.client().config().profiles(),
		name -> this.client().config().create(name),
		name -> this.client().config().delete(name)));
	private final InputSetting saveAs = this.add(new InputSetting("Save As", "Copies this profile's settings (not its data) under a new name and switches to it",
		"name", "Save", name -> this.client().config().saveAs(name)));

	public ProfilesSection() {
		super("Profiles", "Separate settings, proxy and data per profile");
		this.active.onChange(name -> {
			if (!name.isEmpty() && !name.equals(this.client().config().profile())) {
				this.client().config().switchTo(name);
			}
		});
	}

	/** Shows the profile in use; called after loading and after every switch. */
	public void sync(String profile) {
		this.active.set(profile);
	}
}
