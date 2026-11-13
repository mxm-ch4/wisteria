package dev.wisteria.module;

public enum Category {
	RENDER("Render"),
	WORLD("World"),
	PLAYER("Player"),
	MOVEMENT("Movement"),
	COMBAT("Combat"),
	MISC("Misc"),
	HUD("HUD"),
	CLIENT("Client");

	private final String title;

	Category(String title) {
		this.title = title;
	}

	public String title() {
		return this.title;
	}
}
