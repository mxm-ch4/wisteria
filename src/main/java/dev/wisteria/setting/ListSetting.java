package dev.wisteria.setting;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * An editable list kept somewhere else (friends, allowed hosts, proxies): a field and an Add button, then one line
 * per entry with a remove button. In chat: {@code .module name add <text>} and {@code .module name remove <entry>}.
 */
public class ListSetting extends InputSetting {
	private final Supplier<List<String>> entries;
	private final Consumer<String> remove;

	public ListSetting(String name, String description, String placeholder, Supplier<List<String>> entries, Consumer<String> add, Consumer<String> remove) {
		super(name, description, placeholder, "Add", add);
		this.entries = entries;
		this.remove = remove;
	}

	public final List<String> entries() {
		return this.entries.get();
	}

	public final void remove(String entry) {
		this.set(this.get() + 1);
		this.remove.accept(entry);
	}

	@Override
	public String format() {
		List<String> entries = this.entries();
		return entries.isEmpty() ? "(empty)" : String.join(", ", entries);
	}

	@Override
	public void parse(String input) {
		String trimmed = input.strip();
		if (trimmed.regionMatches(true, 0, "remove ", 0, 7)) {
			this.remove(trimmed.substring(7).strip());
		} else if (trimmed.regionMatches(true, 0, "add ", 0, 4)) {
			super.parse(trimmed.substring(4));
		} else {
			super.parse(trimmed);
		}
	}

	@Override
	public List<String> suggestions() {
		return List.of("add", "remove");
	}
}
