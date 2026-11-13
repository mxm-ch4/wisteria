package dev.wisteria.command;

import dev.wisteria.Client;
import dev.wisteria.Wisteria;
import dev.wisteria.compat.ChatBackend;
import dev.wisteria.module.Module;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;

/**
 * Chat commands: anything typed in chat that starts with the prefix (default ".") is run here and never
 * reaches the server. A module name works as a command too: ".esp" toggles, ".esp range 48" sets.
 */
public final class CommandManager {
	public static final String DEFAULT_PREFIX = ".";

	private final Client client;
	private final Map<String, Command> commands = new LinkedHashMap<>();
	private final Map<String, Command> byAlias = new LinkedHashMap<>();
	private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
	private String prefix = DEFAULT_PREFIX;

	public CommandManager(Client client) {
		this.client = client;
		Commands.registerAll(this);
	}

	public void register(Command command) {
		if (this.commands.putIfAbsent(command.name(), command) != null) {
			throw new IllegalStateException("Duplicate command " + command.name());
		}

		this.byAlias.put(command.name(), command);
		for (String alias : command.aliases()) {
			if (this.byAlias.putIfAbsent(alias, command) != null) {
				throw new IllegalStateException("Duplicate command alias " + alias);
			}
		}
	}

	public List<Command> all() {
		return List.copyOf(this.commands.values());
	}

	public @Nullable Command get(String name) {
		return this.byAlias.get(name.toLowerCase(Locale.ROOT));
	}

	public String prefix() {
		return this.prefix;
	}

	public static boolean isValidPrefix(String prefix) {
		// "/" belongs to the game's own commands; letters and digits would swallow ordinary chat.
		return prefix.matches("[^\\sA-Za-z0-9/]{1,3}");
	}

	/** @throws IllegalArgumentException if the prefix is not 1-3 symbols or starts like a game command */
	public void setPrefix(String prefix) {
		if (!isValidPrefix(prefix)) {
			throw new IllegalArgumentException("A prefix is 1-3 symbols, not letters, digits, spaces or /");
		}

		if (!prefix.equals(this.prefix)) {
			this.prefix = prefix;
			for (Runnable listener : this.changeListeners) {
				listener.run();
			}
		}
	}

	public void onChange(Runnable listener) {
		this.changeListeners.add(listener);
	}

	/**
	 * Chat hook.
	 *
	 * @return true if the message was a client command and must not be sent to the server
	 */
	public boolean handleChat(String message) {
		if (!message.startsWith(this.prefix) || message.length() == this.prefix.length()) {
			return false;
		}

		this.execute(message.substring(this.prefix.length()), this.client.platform().chat()::print);
		return true;
	}

	/** Runs a command line given without the prefix. Never throws: problems are reported through {@code out}. */
	public void execute(String line, Command.Output out) {
		List<String> tokens = tokenize(line);
		if (tokens.isEmpty()) {
			return;
		}

		String name = tokens.get(0).toLowerCase(Locale.ROOT);
		List<String> args = tokens.subList(1, tokens.size());
		try {
			Command command = this.byAlias.get(name);
			if (command != null) {
				command.run(this.client, args, out);
			} else if (this.client.modules().get(name) != null) {
				Commands.moduleShorthand(this.client, this.client.modules().get(name), args, out);
			} else {
				out.print("Unknown command \"" + name + "\". Try " + this.prefix + "help");
			}
		} catch (Command.CommandException | IllegalArgumentException e) {
			out.print(e.getMessage());
		} catch (RuntimeException e) {
			Wisteria.LOG.error("Command \"{}\" failed", line, e);
			out.print("That command failed: " + e);
		}
	}

	/** Tab completion for the chat box; null when the input is not a client command. */
	public ChatBackend.@Nullable Completion complete(String input) {
		if (!input.startsWith(this.prefix)) {
			return null;
		}

		String line = input.substring(this.prefix.length());
		// A trailing space means the player has moved on to the next, still empty, argument.
		String[] parts = line.split(" ", -1);
		String partial = parts[parts.length - 1];
		List<String> before = new ArrayList<>(Arrays.asList(parts).subList(0, parts.length - 1));
		before.removeIf(String::isEmpty);

		List<String> candidates;
		if (before.isEmpty()) {
			candidates = new ArrayList<>(this.commands.keySet());
			for (Module module : this.client.modules().all()) {
				candidates.add(module.id());
			}
		} else {
			String name = before.get(0).toLowerCase(Locale.ROOT);
			List<String> args = new ArrayList<>(before.subList(1, before.size()));
			args.add(partial);
			Command command = this.byAlias.get(name);
			Module module = this.client.modules().get(name);
			if (command != null) {
				candidates = command.complete(this.client, args);
			} else if (module != null) {
				candidates = Commands.completeModuleShorthand(module, args);
			} else {
				candidates = List.of();
			}
		}

		String wanted = partial.toLowerCase(Locale.ROOT);
		TreeSet<String> matches = new TreeSet<>();
		for (String candidate : candidates) {
			if (candidate.toLowerCase(Locale.ROOT).startsWith(wanted)) {
				matches.add(candidate);
			}
		}

		return new ChatBackend.Completion(input.length() - partial.length(), List.copyOf(matches));
	}

	static List<String> tokenize(String line) {
		List<String> tokens = new ArrayList<>();
		for (String token : line.trim().split("\\s+")) {
			if (!token.isEmpty()) {
				tokens.add(token);
			}
		}

		return tokens;
	}
}
