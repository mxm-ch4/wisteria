package dev.wisteria.command;

import dev.wisteria.Client;
import java.util.List;

/** One chat command. Arguments arrive split on spaces, without the command name itself. */
public abstract class Command {
	private final String name;
	private final List<String> aliases;
	private final String usage;
	private final String description;

	protected Command(String name, List<String> aliases, String usage, String description) {
		this.name = name;
		this.aliases = aliases;
		this.usage = usage;
		this.description = description;
	}

	public final String name() {
		return this.name;
	}

	public final List<String> aliases() {
		return this.aliases;
	}

	/** Arguments only, e.g. "&lt;module&gt; [on|off]". */
	public final String usage() {
		return this.usage;
	}

	public final String description() {
		return this.description;
	}

	/** @throws CommandException with a message for the player when the arguments make no sense */
	public abstract void run(Client client, List<String> args, Output out);

	/**
	 * Candidates for the last argument, which may be partial or empty. Filtering by what is already typed
	 * is done by the caller.
	 */
	public List<String> complete(Client client, List<String> args) {
		return List.of();
	}

	/** Where a command writes its replies: the chat in the game, a list in tests. */
	public interface Output {
		void print(String line);
	}

	/** A mistake in what the player typed. The message is shown to them as is. */
	public static final class CommandException extends RuntimeException {
		public CommandException(String message) {
			super(message);
		}
	}
}
