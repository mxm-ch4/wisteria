package dev.wisteria.mixin;

import dev.wisteria.platform.game.ProxyConnector;
import dev.wisteria.proxy.ProxyManager;
import java.net.InetSocketAddress;
import java.util.Optional;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerNameResolver.class)
abstract class ServerNameResolverMixin {
	// With a proxy, nothing about the server is looked up here: no DNS, no SRV record; the proxy resolves the name.
	@Inject(method = "resolveAddress", at = @At("HEAD"), cancellable = true)
	private void wisteria$noLocalLookup(ServerAddress address, CallbackInfoReturnable<Optional<ResolvedServerAddress>> cir) {
		if (!(ProxyConnector.route() instanceof ProxyManager.Route.Direct)) {
			cir.setReturnValue(Optional.of(ResolvedServerAddress.from(InetSocketAddress.createUnresolved(address.getHost(), address.getPort()))));
		}
	}
}
