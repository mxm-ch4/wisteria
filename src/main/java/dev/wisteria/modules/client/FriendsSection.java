package dev.wisteria.modules.client;

import dev.wisteria.setting.ListSetting;
import dev.wisteria.social.Relations;

/** Friends and enemies: shared by every profile; ESP colours players by them. */
public final class FriendsSection extends ClientSection {
	public FriendsSection() {
		super("Friends", "Friends and enemies lists");
		this.add(new ListSetting("Friends", "Players drawn in the friend colour", "player name",
			() -> this.client().relations().friends(), name -> this.set(name, Relations.Relation.FRIEND), name -> this.clear(name, Relations.Relation.FRIEND)));
		this.add(new ListSetting("Enemies", "Players drawn in the enemy colour", "player name",
			() -> this.client().relations().enemies(), name -> this.set(name, Relations.Relation.ENEMY), name -> this.clear(name, Relations.Relation.ENEMY)));
	}

	private void set(String name, Relations.Relation relation) {
		if (!Relations.isValidName(name)) {
			throw new IllegalArgumentException("\"" + name + "\" does not look like a player name");
		}

		this.client().relations().set(name, relation);
	}

	private void clear(String name, Relations.Relation relation) {
		if (this.client().relations().of(name) == relation) {
			this.client().relations().set(name, Relations.Relation.NEUTRAL);
		}
	}
}
