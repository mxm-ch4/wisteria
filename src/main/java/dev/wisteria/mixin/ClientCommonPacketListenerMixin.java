package dev.wisteria.mixin;

import dev.wisteria.Wisteria;
import dev.wisteria.event.Events;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
abstract class ClientCommonPacketListenerMixin {
	@Inject(method = "onDisconnect", at = @At("HEAD"))
	private void wisteria$disconnect(DisconnectionDetails details, CallbackInfo ci) {
		Wisteria.post(new Events.Disconnect(details.reason()));
	}
}
