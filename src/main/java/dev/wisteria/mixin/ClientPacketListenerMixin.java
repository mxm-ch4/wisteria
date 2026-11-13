package dev.wisteria.mixin;

import dev.wisteria.platform.game.GameEvents;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Handlers first run on the network thread and bail out to reschedule themselves on the client thread;
// only that second run reaches TAIL, so these fire once, on the client thread.
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
	@Inject(method = "handleOpenScreen", at = @At("TAIL"))
	private void wisteria$openScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
		Identifier menu = BuiltInRegistries.MENU.getKey(packet.getType());
		GameEvents.containerOpened(packet.getContainerId(), menu != null ? menu.toString() : "unknown", packet.getTitle());
	}

	@Inject(method = "handleContainerContent", at = @At("TAIL"))
	private void wisteria$containerContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
		GameEvents.containerContent(packet.containerId(), packet.items());
	}
}
