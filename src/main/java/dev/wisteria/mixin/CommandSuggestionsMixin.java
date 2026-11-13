package dev.wisteria.mixin;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.wisteria.platform.ChatBackend;
import dev.wisteria.platform.game.GamePlatform;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tab completion for client commands in the chat box. The game recomputes its suggestions in
 * updateCommandInfo() on every edit; when the text is one of ours we supply the list and skip the game's
 * own command parsing, which would only see an unknown chat message.
 */
@Mixin(CommandSuggestions.class)
abstract class CommandSuggestionsMixin {
	@Shadow
	@Final
	private EditBox input;

	@Shadow
	private @Nullable CompletableFuture<Suggestions> pendingSuggestions;

	// Injected where the game starts parsing, i.e. after it has cleared the previous suggestions.
	@Inject(method = "updateCommandInfo", at = @At(value = "NEW", target = "com/mojang/brigadier/StringReader"), cancellable = true)
	private void wisteria$complete(CallbackInfo ci) {
		GamePlatform platform = GamePlatform.instance();
		if (platform == null) {
			return;
		}

		String text = this.input.getValue();
		String beforeCursor = text.substring(0, Math.min(this.input.getCursorPosition(), text.length()));
		ChatBackend.Completion completion = platform.gameChat().complete(beforeCursor);
		if (completion == null) {
			return;
		}

		SuggestionsBuilder builder = new SuggestionsBuilder(beforeCursor, completion.start());
		for (String candidate : completion.candidates()) {
			builder.suggest(candidate);
		}

		this.pendingSuggestions = builder.buildFuture();
		ci.cancel();
	}
}
