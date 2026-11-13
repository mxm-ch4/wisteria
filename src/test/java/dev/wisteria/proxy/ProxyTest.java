package dev.wisteria.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wisteria.Client;
import dev.wisteria.FakePlatform;
import dev.wisteria.io.Net;
import dev.wisteria.modules.client.ProxySection;
import dev.wisteria.setting.PasswordSetting;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProxyTest {
	@TempDir
	Path game;

	@Test
	void parsesWhatTheFieldTakes() {
		ProxyConfig socks = ProxyConfig.parse("work socks5 10.0.0.2:1080");
		assertEquals(new ProxyConfig("work", ProxyConfig.Type.SOCKS5, "10.0.0.2", 1080), socks);
		assertEquals("work — socks5 10.0.0.2:1080", socks.describe());
		assertEquals(ProxyConfig.Type.HTTP, ProxyConfig.parse("office http proxy.example.com:3128").type());
		assertEquals("PROXY_MY_VPS_PASSWORD", ProxyConfig.parse("my-vps socks5 1.2.3.4:1").passwordKey());
		for (String bad : List.of("", "x socks5", "x ftp h:1", "x socks5 h", "x socks5 h:0", "x socks5 h:99999", "bad name socks5 h:1", "x socks5 h/x:1")) {
			assertThrows(IllegalArgumentException.class, () -> ProxyConfig.parse(bad), bad);
		}
	}

	@Test
	void theRouteIsLeakProof() {
		FakePlatform platform = new FakePlatform(this.game);
		Client client = new Client(platform);
		assertInstanceOf(ProxyManager.Route.Direct.class, client.proxies().route(), "off: direct");
		platform.typeInChat(".toggle proxy on");
		assertInstanceOf(ProxyManager.Route.Blocked.class, client.proxies().route(), "on with none chosen: nothing connects");
		platform.typeInChat(".proxy proxies add work socks5 127.0.0.1:1080");
		platform.typeInChat(".proxy proxy work");
		ProxyManager.Route route = client.proxies().route();
		assertInstanceOf(ProxyManager.Route.Via.class, route);
		assertEquals("work", ((ProxyManager.Route.Via)route).proxy().name());
		platform.typeInChat(".proxy proxies remove work");
		assertInstanceOf(ProxyManager.Route.Blocked.class, client.proxies().route(), "the chosen proxy is gone: still no direct connection");
	}

	@Test
	void loginGoesToTheSecretsFileOnlyAndNeverThroughChat() throws IOException {
		FakePlatform platform = new FakePlatform(this.game);
		Client client = new Client(platform);
		platform.typeInChat(".proxy proxies add work socks5 127.0.0.1:1080");
		platform.typeInChat(".proxy proxy work");
		platform.typeInChat(".toggle proxy on");
		platform.typeInChat(".proxy password hunter2-secret");
		assertTrue(platform.chatLines.getLast().contains("only be typed into its field"), platform.chatLines.getLast());

		ProxySection section = client.modules().get(ProxySection.class);
		((PasswordSetting)section.setting("login")).store("alice");
		((PasswordSetting)section.setting("password")).store("hunter2-secret");
		ProxyManager.Route.Via via = (ProxyManager.Route.Via)client.proxies().route();
		assertEquals("alice", via.user());
		assertEquals("hunter2-secret", via.password());
		assertFalse(via.toString().contains("hunter2"), via.toString());
		assertEquals("****", section.setting("password").format());
		platform.stop();

		String env = Files.readString(this.game.resolve("wisteria/.env"));
		assertTrue(env.contains("PROXY_WORK_PASSWORD=hunter2-secret") && env.contains("PROXY_WORK_USER=alice"));
		try (var files = Files.walk(this.game.resolve("wisteria"))) {
			for (Path file : files.filter(Files::isRegularFile).filter(file -> file.toString().endsWith(".json")).toList()) {
				assertFalse(Files.readString(file).contains("hunter2"), "a password in " + file);
			}
		}

		platform.typeInChat(".proxy proxies remove work");
		assertFalse(Files.readString(this.game.resolve("wisteria/.env")).contains("PROXY_WORK"), "removing a proxy forgets its login");
	}

	@Test
	void proxiesAndTheChoiceSurviveARestart() {
		FakePlatform platform = new FakePlatform(this.game);
		new Client(platform);
		platform.typeInChat(".proxy proxies add work socks5 127.0.0.1:1080");
		platform.typeInChat(".proxy proxy work");
		platform.typeInChat(".toggle proxy on");
		platform.stop();
		Client after = new Client(new FakePlatform(this.game));
		assertEquals(List.of("work"), after.proxies().names());
		assertInstanceOf(ProxyManager.Route.Via.class, after.proxies().route());
	}

	@Test
	void theClientsOwnNetworkIsOffWhileAProxyIsOn() {
		FakePlatform platform = new FakePlatform(this.game);
		Client client = new Client(platform);
		client.net().allow("127.0.0.1");
		platform.typeInChat(".toggle proxy on");
		CompletionException error = assertThrows(CompletionException.class,
			() -> client.net().send("notifier", Net.Request.get("http://127.0.0.1:9/")).join());
		assertTrue(error.getCause().getMessage().contains("a proxy is on"), error.getCause().getMessage());
	}

	@Test
	void theTestNeedsAnAllowedHost() {
		FakePlatform platform = new FakePlatform(this.game);
		Client client = new Client(platform);
		platform.typeInChat(".proxy proxies add work socks5 127.0.0.1:1080");
		platform.typeInChat(".proxy proxy work");
		platform.typeInChat(".toggle proxy on");
		client.proxies().test("api.ipify.org");
		assertTrue(platform.chatLines.getLast().contains("allow api.ipify.org"), platform.chatLines.getLast());
	}

	// ---- the tunnel, against small local proxies ----

	@Test
	void socks5TunnelSendsTheNameNotAnAddress() throws Exception {
		List<String> asked = new CopyOnWriteArrayList<>();
		try (ServerSocket echo = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
			ServerSocket proxy = new ServerSocket(0, 5, InetAddress.getLoopbackAddress())) {
			serve(echo, socket -> socket.getOutputStream().write("hello".getBytes(StandardCharsets.US_ASCII)));
			serve(proxy, client -> {
				InputStream in = client.getInputStream();
				OutputStream out = client.getOutputStream();
				int methods = in.readNBytes(2)[1];
				byte[] offered = in.readNBytes(methods);
				boolean login = offered.length > 1;
				out.write(new byte[] {5, (byte)(login ? 2 : 0)});
				if (login) {
					in.read();
					String user = new String(in.readNBytes(in.read()), StandardCharsets.UTF_8);
					String pass = new String(in.readNBytes(in.read()), StandardCharsets.UTF_8);
					asked.add("login " + user + "/" + pass);
					out.write(new byte[] {1, 0});
				}

				byte[] head = in.readNBytes(4);
				String host = new String(in.readNBytes(in.read()), StandardCharsets.US_ASCII);
				byte[] port = in.readNBytes(2);
				asked.add((head[3] == 3 ? "name " : "address ") + host + ":" + ((port[0] & 0xFF) << 8 | port[1] & 0xFF));
				out.write(new byte[] {5, 0, 0, 1, 127, 0, 0, 1, 0, 0});
				try (Socket upstream = new Socket(InetAddress.getLoopbackAddress(), echo.getLocalPort())) {
					out.write(upstream.getInputStream().readNBytes(5));
				}
			});
			ProxyConfig config = new ProxyConfig("t", ProxyConfig.Type.SOCKS5, "127.0.0.1", proxy.getLocalPort());
			try (Socket tunnel = ProxyTunnel.open(config, "alice", "pw", "server.example", 25565, 3000)) {
				assertEquals("hello", new String(tunnel.getInputStream().readNBytes(5), StandardCharsets.US_ASCII));
			}

			assertEquals(List.of("login alice/pw", "name server.example:25565"), asked);
		}
	}

	@Test
	void httpConnectTunnel() throws Exception {
		List<String> asked = new CopyOnWriteArrayList<>();
		try (ServerSocket proxy = new ServerSocket(0, 5, InetAddress.getLoopbackAddress())) {
			serve(proxy, client -> {
				StringBuilder head = new StringBuilder();
				InputStream in = client.getInputStream();
				while (!head.toString().endsWith("\r\n\r\n")) {
					head.append((char)in.read());
				}

				asked.add(head.toString().lines().findFirst().orElse(""));
				asked.add(head.toString().contains("Proxy-Authorization: Basic ") ? "auth" : "no auth");
				client.getOutputStream().write("HTTP/1.1 200 Connection established\r\n\r\nhi".getBytes(StandardCharsets.US_ASCII));
			});
			ProxyConfig config = new ProxyConfig("t", ProxyConfig.Type.HTTP, "127.0.0.1", proxy.getLocalPort());
			try (Socket tunnel = ProxyTunnel.open(config, "bob", "pw", "api.example.org", 443, 3000)) {
				assertEquals("hi", new String(tunnel.getInputStream().readNBytes(2), StandardCharsets.US_ASCII));
			}

			assertEquals(List.of("CONNECT api.example.org:443 HTTP/1.1", "auth"), asked);
		}
	}

	@Test
	void aDeadProxyFailsInsteadOfConnectingDirectly() throws IOException {
		int port;
		try (ServerSocket closed = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			port = closed.getLocalPort();
		}

		ProxyConfig config = new ProxyConfig("t", ProxyConfig.Type.SOCKS5, "127.0.0.1", port);
		assertThrows(IOException.class, () -> ProxyTunnel.open(config, null, null, "example.org", 443, 1000));
	}

	private interface Session {
		void run(Socket socket) throws IOException;
	}

	private static void serve(ServerSocket server, Session session) {
		Thread thread = new Thread(() -> {
			try (Socket socket = server.accept()) {
				session.run(socket);
			} catch (IOException ignored) {
				// test ended
			}
		});
		thread.setDaemon(true);
		thread.start();
	}
}
