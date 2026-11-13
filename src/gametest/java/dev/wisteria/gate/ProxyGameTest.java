package dev.wisteria.gate;

import static dev.wisteria.gate.GameTestKit.check;

import dev.wisteria.Client;
import dev.wisteria.Wisteria;
import dev.wisteria.modules.client.ProxySection;
import dev.wisteria.proxy.ProxyConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A real server on this machine, joined directly and then through a SOCKS5 proxy that leaves from another loopback
 * address: the server must see the proxy's address, the proxy must get the server's name (not an address), and with
 * the proxy down the client must not connect at all.
 */
public final class ProxyGameTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("WisteriaGate");

	@Override
	public void runTest(ClientGameTestContext context) {
		if (GameTestKit.isAuditPhase()) {
			return;
		}

		if (GameTestKit.isRestartPhase()) {
			return;
		}

		Client client = Wisteria.client();
		ProxySection section = context.computeOnClient(minecraft -> client.modules().get(ProxySection.class));
		try (TestSocksProxy proxy = new TestSocksProxy(); TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			int port = server.computeOnServer(minecraft -> minecraft.getPort());

			try (TestDedicatedServerConnection connection = server.connect()) {
				String seen = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().connection.getRemoteAddress().toString());
				LOG.info("proxy: directly, the server sees {}", seen);
				check("proxy: without a proxy the server sees 127.0.0.1", seen.contains("127.0.0.1"));
			}

			context.runOnClient(minecraft -> {
				client.proxies().add(ProxyConfig.parse("gate socks5 127.0.0.1:" + proxy.port()));
				section.setting("proxy").parse("gate");
				client.setEnabled(section, true);
			});
			try (TestDedicatedServerConnection connection = server.connect()) {
				String seen = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().connection.getRemoteAddress().toString());
				LOG.info("proxy: through the proxy, the server sees {}; the proxy was asked for {}", seen, proxy.requests);
				check("proxy: through the proxy the server sees the proxy's address, " + TestSocksProxy.EXIT_ADDRESS, seen.contains(TestSocksProxy.EXIT_ADDRESS));
				check("proxy: the server's name went to the proxy unresolved", proxy.requests.contains("name localhost"));
			}

			// The proxy goes away: the connection must fail, not fall back to direct.
			proxy.close();
			context.runOnClient(minecraft -> ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString("localhost:" + port),
				new ServerData("gate", "localhost:" + port, ServerData.Type.OTHER), false, null));
			context.waitFor(minecraft -> GateHooks.currentScreen(minecraft) instanceof DisconnectedScreen, 600);
			context.waitTicks(20);
			check("proxy: with the proxy down nothing reached the server", server.computeOnServer(s -> s.getPlayerList().getPlayers().isEmpty()));
			context.setScreen(TitleScreen::new);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			context.runOnClient(minecraft -> {
				client.setEnabled(section, false);
				section.setting("proxy").parse("none");
				client.proxies().remove("gate");
			});
		}
	}
}
