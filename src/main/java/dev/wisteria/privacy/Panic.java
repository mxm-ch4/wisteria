package dev.wisteria.privacy;

import dev.wisteria.Client;
import dev.wisteria.module.Module;
import java.util.ArrayList;
import java.util.List;

/**
 * The panic button: in one tick, every module off, the client's screens closed, its HUD, toasts and chat lines hidden,
 * background work stopped, held keys let go; optionally leave the world or quit the game the normal way. Nothing is
 * sent to the server (unless leaving the world was chosen) and nothing on disk is touched. {@link #restore()} brings
 * back the modules that were on.
 */
public final class Panic {
	private final Client client;
	private final List<String> remembered = new ArrayList<>();
	private volatile boolean active;

	public Panic(Client client) {
		this.client = client;
	}

	/** True from the panic until Restore: the client draws, prints and toasts nothing. */
	public boolean active() {
		return this.active;
	}

	/** The modules that were on when the panic hit, by id, in order. */
	public List<String> remembered() {
		return List.copyOf(this.remembered);
	}

	/**
	 * Hides the client now. Client thread. A second panic while one is active changes nothing (the first list stays).
	 *
	 * @param leaveWorld also disconnect from the server or world, the game's own way
	 * @param quitGame also close the game, the game's own way
	 */
	public void trigger(boolean leaveWorld, boolean quitGame) {
		if (!this.active) {
			this.active = true;
			this.remembered.clear();
			for (Module module : this.client.modules().all()) {
				if (module.isToggleable() && module.isEnabled()) {
					this.remembered.add(module.id());
				}
			}

			// Off in reverse, so a module started after another one stops before it.
			List<Module> on = new ArrayList<>();
			for (String id : this.remembered) {
				on.add(this.client.modules().get(id));
			}

			for (int i = on.size() - 1; i >= 0; i--) {
				this.client.setEnabled(on.get(i), false);
			}

			this.client.platform().screens().close();
			this.client.platform().chat().hideClientLines();
			this.client.hud().toasts().clear();
			this.client.platform().input().releaseAll();
		}

		if (quitGame) {
			this.client.platform().quitGame();
		} else if (leaveWorld) {
			this.client.platform().leaveWorld();
		}
	}

	/** Brings back the modules that were on before the panic. Client thread. */
	public void restore() {
		if (!this.active) {
			return;
		}

		this.active = false;
		for (String id : this.remembered) {
			Module module = this.client.modules().get(id);
			if (module != null && !module.isEnabled()) {
				this.client.setEnabled(module, true);
			}
		}

		this.remembered.clear();
	}
}
