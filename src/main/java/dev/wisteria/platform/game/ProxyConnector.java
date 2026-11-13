package dev.wisteria.platform.game;

import dev.wisteria.Client;
import dev.wisteria.Wisteria;
import dev.wisteria.proxy.ProxyManager;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.resolver.NoopAddressResolverGroup;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.jspecify.annotations.Nullable;

/**
 * The game's connection to a server, through a proxy: the same pipeline the game builds, with Netty's SOCKS5 or
 * HTTP CONNECT handler in front, and the server's name handed to the proxy unresolved, so the proxy looks it up.
 * There is no fallback: if the proxy does not answer, the connection fails.
 */
public final class ProxyConnector {
	private static final int PROXY_TIMEOUT_MILLIS = 10_000;

	private ProxyConnector() {
	}

	/** The route for the next connection, or Direct when the client is not running yet. */
	public static ProxyManager.Route route() {
		Client client = Wisteria.client();
		return client == null ? new ProxyManager.Route.Direct() : client.proxies().route();
	}

	public static ChannelFuture connect(InetSocketAddress address, EventLoopGroupHolder group, Connection connection, @Nullable BandwidthDebugMonitor monitor,
		ProxyManager.Route route) {
		if (route instanceof ProxyManager.Route.Blocked blocked) {
			Wisteria.LOG.warn("Connection blocked: {}", blocked.reason());
			notifyBlocked(blocked.reason());
			// A future that has already failed: the game shows the reason and nothing was opened anywhere.
			return new io.netty.channel.embedded.EmbeddedChannel().newFailedFuture(new ConnectException("Wisteria: " + blocked.reason()));
		}

		ProxyManager.Route.Via via = (ProxyManager.Route.Via)route;
		InetSocketAddress target = InetSocketAddress.createUnresolved(address.getHostString(), address.getPort());
		Wisteria.LOG.info("Connecting to {} through proxy {}", target, via.proxy().describe());
		ChannelFuture future = new Bootstrap().group(group.eventLoopGroup())
			// The proxy resolves the server's name; nothing is looked up here.
			.resolver(NoopAddressResolverGroup.INSTANCE)
			.handler(new ChannelInitializer<Channel>() {
				@Override
				protected void initChannel(Channel channel) {
					try {
						channel.config().setOption(ChannelOption.TCP_NODELAY, true);
					} catch (ChannelException ignored) {
						// not every transport has it
					}

					channel.pipeline().addFirst("wisteria_proxy", handler(via));
					channel.pipeline().addLast("timeout", new ReadTimeoutHandler(30));
					Connection.configureSerialization(channel.pipeline(), PacketFlow.CLIENTBOUND, false, monitor);
					connection.configurePacketHandler(channel.pipeline());
				}
			})
			.channel(group.channelCls())
			.connect(target);
		future.addListener(done -> {
			if (!done.isSuccess()) {
				Wisteria.LOG.warn("Proxy {} failed; the connection was blocked, not made directly: {}", via.proxy().name(), done.cause().toString());
				notifyBlocked("proxy " + via.proxy().name() + " did not connect; nothing was sent directly");
			}
		});
		return future;
	}

	static ProxyHandler handler(ProxyManager.Route.Via via) {
		InetSocketAddress proxy = new InetSocketAddress(via.proxy().host(), via.proxy().port());
		boolean login = via.user() != null && !via.user().isEmpty();
		ProxyHandler handler = switch (via.proxy().type()) {
			case SOCKS5 -> login ? new Socks5ProxyHandler(proxy, via.user(), via.password() == null ? "" : via.password()) : new Socks5ProxyHandler(proxy);
			case HTTP -> login ? new HttpProxyHandler(proxy, via.user(), via.password() == null ? "" : via.password()) : new HttpProxyHandler(proxy);
		};
		handler.setConnectTimeoutMillis(PROXY_TIMEOUT_MILLIS);
		return handler;
	}

	private static void notifyBlocked(String reason) {
		Client client = Wisteria.client();
		if (client != null) {
			client.platform().execute(() -> client.hud().notify("Connection blocked", reason, dev.wisteria.hud.Toasts.Kind.WARNING));
		}
	}
}
