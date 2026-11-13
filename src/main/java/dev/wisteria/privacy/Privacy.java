package dev.wisteria.privacy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.wisteria.Client;
import dev.wisteria.proxy.ProxyConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * What the client lets out about itself: the brand it reports, whether the mod's network channels are announced,
 * and the guard on outgoing chat. The switches live in Client → Privacy; the guard's word list in client.json.
 */
public final class Privacy {
	/** Words a line may not mention by default: the client's name and the module names that are not everyday words. */
	public static final List<String> DEFAULT_WORDS = List.of("wisteria", "clickgui", "click gui", "fullbright", "hud editor", "hudeditor", "arraylist", "chams");

	private final Client client;
	private final List<String> words = new CopyOnWriteArrayList<>(DEFAULT_WORDS);
	private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
	private final AtomicInteger guarded = new AtomicInteger();
	private volatile Supplier<String> brand = () -> "vanilla";
	private volatile BooleanSupplier hideChannels = () -> true;
	private volatile BooleanSupplier guard = () -> true;

	public Privacy(Client client) {
		this.client = client;
	}

	/** Wired to the Privacy section's settings. */
	public void settings(Supplier<String> brand, BooleanSupplier hideChannels, BooleanSupplier guard) {
		this.brand = brand;
		this.hideChannels = hideChannels;
		this.guard = guard;
	}

	/** The brand the server is told: "vanilla" unless the user chose otherwise. Any thread. */
	public String brand() {
		String chosen = this.brand.get().strip();
		return chosen.isEmpty() ? "vanilla" : chosen;
	}

	/** True to send no mod channel list and no mod payloads at all. Any thread. */
	public boolean hideChannels() {
		return this.hideChannels.getAsBoolean();
	}

	public List<String> words() {
		return List.copyOf(this.words);
	}

	public void addWord(String word) {
		String trimmed = word.strip().toLowerCase(Locale.ROOT);
		if (trimmed.isEmpty() || trimmed.length() > 48) {
			throw new IllegalArgumentException("A guard word is 1 to 48 characters");
		}

		if (!this.words.contains(trimmed)) {
			this.words.add(trimmed);
			this.changed();
		}
	}

	public void removeWord(String word) {
		if (this.words.remove(word.strip().toLowerCase(Locale.ROOT))) {
			this.changed();
		}
	}

	/**
	 * Checks an outgoing chat line or command; when it would leak, counts it and says why.
	 *
	 * @return the reason it was held back, or null to let it go
	 */
	public @Nullable String guard(String line) {
		if (!this.guard.getAsBoolean() || line.startsWith(this.client.commands().prefix())) {
			return null;
		}

		List<String> proxyBits = new ArrayList<>();
		for (ProxyConfig proxy : this.client.proxies().proxies()) {
			proxyBits.add(proxy.host());
			proxyBits.add(proxy.address());
			this.client.secrets().forCore(proxy.userKey()).ifPresent(proxyBits::add);
		}

		String reason = ChatGuard.check(line, this.client.secrets().values(), proxyBits, this.words);
		if (reason != null) {
			this.guarded.incrementAndGet();
		}

		return reason;
	}

	/** How many lines the guard has held back since the game started. */
	public int guardCount() {
		return this.guarded.get();
	}

	public void onChange(Runnable listener) {
		this.changeListeners.add(listener);
	}

	private void changed() {
		this.changeListeners.forEach(Runnable::run);
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		JsonArray array = new JsonArray();
		this.words.forEach(array::add);
		json.add("guardWords", array);
		return json;
	}

	public void load(JsonObject json) {
		JsonElement words = json.get("guardWords");
		if (words != null && words.isJsonArray()) {
			this.words.clear();
			for (JsonElement word : words.getAsJsonArray()) {
				if (word.isJsonPrimitive() && !word.getAsString().isBlank()) {
					this.words.add(word.getAsString().strip().toLowerCase(Locale.ROOT));
				}
			}
		}
	}
}
