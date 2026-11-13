package dev.wisteria.mixin;

import dev.wisteria.platform.game.ProxyConnector;
import dev.wisteria.proxy.ProxyManager;
import io.netty.channel.ChannelFuture;
import java.net.InetSocketAddress;
import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.network.Connection;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Connection.class)
abstract class ConnectionProxyMixin {
	@Shadow
	private BandwidthDebugMonitor bandwidthDebugMonitor;

	// Every remote connection the game opens (joining, the server list's pings) comes through here.
	@Inject(method = "connect", at = @At("HEAD"), cancellable = true)
	private static void wisteria$proxy(InetSocketAddress address, EventLoopGroupHolder group, Connection connection, CallbackInfoReturnable<ChannelFuture> cir) {
		ProxyManager.Route route = ProxyConnector.route();
		if (!(route instanceof ProxyManager.Route.Direct)) {
			cir.setReturnValue(ProxyConnector.connect(address, group, connection, ((ConnectionProxyMixin)(Object)connection).bandwidthDebugMonitor, route));
		}
	}
}
