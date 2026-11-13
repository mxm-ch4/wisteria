package dev.wisteria.mixin;

import dev.wisteria.Client;
import dev.wisteria.Wisteria;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class ChatSendGuardMixin {
	// Every chat line and command on its way to the server, typed or sent by code, passes the chat guard first.
	@Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
	private void wisteria$guardChat(String content, CallbackInfo ci) {
		if (hold(content)) {
			ci.cancel();
		}
	}

	@Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
	private void wisteria$guardCommand(String command, CallbackInfo ci) {
		if (hold("/" + command)) {
			ci.cancel();
		}
	}

	private static boolean hold(String line) {
		Client client = Wisteria.clientOrNull();
		String reason = client != null ? client.privacy().guard(line) : null;
		if (reason == null) {
			return false;
		}

		client.platform().chat().print("Not sent: " + reason + " (Client → Privacy → Chat Guard)");
		return true;
	}
}
