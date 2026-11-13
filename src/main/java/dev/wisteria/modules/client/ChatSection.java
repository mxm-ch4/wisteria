package dev.wisteria.modules.client;

import dev.wisteria.setting.BoolSetting;
import dev.wisteria.setting.InputSetting;

/**
 * Chat commands. Whatever starts with the prefix never reaches the server, even with commands off: it is dropped
 * with a note. Everything a command does can also be done in this menu.
 */
public final class ChatSection extends ClientSection {
	private final BoolSetting commands = this.add(new BoolSetting("Chat Commands", "Run commands typed in chat; off still keeps them from the server", true));

	public ChatSection() {
		super("Chat", "Command prefix and chat commands");
		this.add(new InputSetting("Prefix", "What client commands start with", ".", "Set", prefix -> this.client().commands().setPrefix(prefix)));
	}

	public boolean commandsEnabled() {
		return this.commands.get();
	}
}
