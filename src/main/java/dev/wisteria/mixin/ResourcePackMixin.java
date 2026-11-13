package dev.wisteria.mixin;

import dev.wisteria.Client;
import dev.wisteria.Wisteria;
import dev.wisteria.modules.client.ProxySection;
import dev.wisteria.platform.game.ProxyConnector;
import dev.wisteria.proxy.ProxyManager;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
abstract class ResourcePackMixin {
	@Shadow
	@Final
	protected Connection connection;

	// The game downloads a server's pack straight from its URL, from this machine: with a proxy that would show the real
	// address to whoever hosts the pack. So, unless the profile says otherwise, the pack is declined.
	@Inject(method = "handleResourcePackPush", at = @At("HEAD"), cancellable = true)
	private void wisteria$declineWithProxy(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
		Client client = Wisteria.client();
		ProxySection section = client != null ? client.modules().get(ProxySection.class) : null;
		if (section == null || ProxyConnector.route() instanceof ProxyManager.Route.Direct || section.packs() == ProxySection.Packs.ACCEPT) {
			return;
		}

		this.connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.DECLINED));
		client.platform().execute(() -> client.platform().chat().print("Declined the server's resource pack: downloading it would bypass the proxy"
			+ " (Client → Proxy → Server Packs)"));
		ci.cancel();
	}
}
