package dev.wisteria.mixin;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The chat's history, so the panic button can take the client's own lines out of it. */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
	@Accessor("allMessages")
	List<GuiMessage> wisteria$allMessages();

	@Invoker("refreshTrimmedMessages")
	void wisteria$refreshTrimmedMessages();
}
