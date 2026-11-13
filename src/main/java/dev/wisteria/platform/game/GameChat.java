package dev.wisteria.platform.game;

import dev.wisteria.platform.ChatBackend;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GameChat implements ChatBackend {
	private static final Logger LOG = LoggerFactory.getLogger("Wisteria");
	private static final int ACCENT = 0xB79CFF;
	private static final int TEXT = 0xECE8F5;

	private final GamePlatform platform;
	private final List<ChatListener> listeners = new CopyOnWriteArrayList<>();
	private volatile @Nullable CompletionProvider completions;

	GameChat(GamePlatform platform) {
		this.platform = platform;
	}

	void install() {
		// Returning false keeps the message from being sent.
		ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
			for (ChatListener listener : this.listeners) {
				if (listener.onSend(message)) {
					return false;
				}
			}

			return true;
		});
	}

	@Override
	public void print(String message) {
		LOG.info("[chat] {}", message);
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(() -> {
			// The chat only exists once the game has finished loading; before that the log line above is all there is.
			if (minecraft.player == null) {
				return;
			}

			Component line = Component.literal("Wisteria ").withColor(ACCENT)
				.append(Component.literal("» ").withColor(0x9A92AD))
				.append(Component.literal(message).withColor(TEXT));
			this.platform.chatComponent().addClientSystemMessage(line);
		});
	}

	@Override
	public void listen(ChatListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void completions(CompletionProvider provider) {
		this.completions = provider;
	}

	/** Called by the suggestions mixin with the text before the cursor. */
	public @Nullable Completion complete(String input) {
		CompletionProvider provider = this.completions;
		return provider != null ? provider.complete(input) : null;
	}
}
