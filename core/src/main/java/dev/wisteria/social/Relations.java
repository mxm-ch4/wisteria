package dev.wisteria.social;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Friends and enemies by player name, case-insensitive. A name is in at most one list. */
public final class Relations {
	public enum Relation {
		FRIEND, ENEMY, NEUTRAL
	}

	// lower-case name -> name as first entered
	private final Map<String, String> friends = new TreeMap<>();
	private final Map<String, String> enemies = new TreeMap<>();
	private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

	public static boolean isValidName(String name) {
		return name.matches("[A-Za-z0-9_.*]{1,32}");
	}

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	public synchronized Relation of(String name) {
		String key = key(name);
		if (this.friends.containsKey(key)) {
			return Relation.FRIEND;
		}

		return this.enemies.containsKey(key) ? Relation.ENEMY : Relation.NEUTRAL;
	}

	public boolean isFriend(String name) {
		return this.of(name) == Relation.FRIEND;
	}

	public boolean isEnemy(String name) {
		return this.of(name) == Relation.ENEMY;
	}

	/**
	 * Puts the name in the given list, taking it out of the other one; NEUTRAL removes it from both.
	 *
	 * @return true if anything changed
	 */
	public boolean set(String name, Relation relation) {
		boolean changed;
		synchronized (this) {
			String key = key(name);
			Relation before = this.of(name);
			if (before == relation) {
				return false;
			}

			this.friends.remove(key);
			this.enemies.remove(key);
			if (relation == Relation.FRIEND) {
				this.friends.put(key, name);
			} else if (relation == Relation.ENEMY) {
				this.enemies.put(key, name);
			}

			changed = true;
		}

		for (Runnable listener : this.changeListeners) {
			listener.run();
		}

		return changed;
	}

	public synchronized List<String> friends() {
		return List.copyOf(this.friends.values());
	}

	public synchronized List<String> enemies() {
		return List.copyOf(this.enemies.values());
	}

	/** Replaces both lists without notifying listeners; used when loading from disk. */
	public synchronized void load(List<String> friends, List<String> enemies) {
		this.friends.clear();
		this.enemies.clear();
		for (String name : friends) {
			if (isValidName(name)) {
				this.friends.put(key(name), name);
			}
		}

		for (String name : enemies) {
			if (isValidName(name) && !this.friends.containsKey(key(name))) {
				this.enemies.put(key(name), name);
			}
		}
	}

	public void onChange(Runnable listener) {
		this.changeListeners.add(listener);
	}
}
