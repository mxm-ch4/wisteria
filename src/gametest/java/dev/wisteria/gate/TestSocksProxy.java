package dev.wisteria.gate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A tiny SOCKS5 proxy for the gate: no login, CONNECT only, and it dials out from 127.0.0.2, so the server can tell
 * a proxied connection (from 127.0.0.2) from a direct one (from 127.0.0.1). Records the destinations it was asked
 * for, names included, to show the name was resolved here and not by the client.
 */
final class TestSocksProxy implements AutoCloseable {
	static final String EXIT_ADDRESS = "127.0.0.2";

	final List<String> requests = new CopyOnWriteArrayList<>();
	private final ServerSocket server;
	private volatile boolean running = true;

	TestSocksProxy() throws IOException {
		this.server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
		Thread accept = new Thread(this::accept, "Test SOCKS5 proxy");
		accept.setDaemon(true);
		accept.start();
	}

	int port() {
		return this.server.getLocalPort();
	}

	private void accept() {
		while (this.running) {
			try {
				Socket client = this.server.accept();
				Thread thread = new Thread(() -> this.serve(client), "Test SOCKS5 session");
				thread.setDaemon(true);
				thread.start();
			} catch (IOException e) {
				return;
			}
		}
	}

	private void serve(Socket client) {
		try (client) {
			InputStream in = client.getInputStream();
			OutputStream out = client.getOutputStream();
			byte[] greeting = in.readNBytes(2);
			in.readNBytes(greeting[1]);
			out.write(new byte[] {5, 0});
			byte[] head = in.readNBytes(4);
			String host;
			if (head[3] == 3) {
				int length = in.read();
				host = new String(in.readNBytes(length), StandardCharsets.US_ASCII);
				this.requests.add("name " + host);
			} else {
				byte[] address = in.readNBytes(head[3] == 1 ? 4 : 16);
				host = InetAddress.getByAddress(address).getHostAddress();
				this.requests.add("address " + host);
			}

			byte[] portBytes = in.readNBytes(2);
			int port = (portBytes[0] & 0xFF) << 8 | portBytes[1] & 0xFF;
			Socket upstream = new Socket();
			upstream.bind(new InetSocketAddress(EXIT_ADDRESS, 0));
			upstream.connect(new InetSocketAddress(host, port), 5000);
			out.write(new byte[] {5, 0, 0, 1, 127, 0, 0, 2, 0, 0});
			out.flush();
			try (upstream) {
				Thread up = new Thread(() -> pipe(in, upstream), "Test SOCKS5 pipe");
				up.setDaemon(true);
				up.start();
				pipe(upstream.getInputStream(), out);
			}
		} catch (IOException ignored) {
			// the session ends
		}
	}

	private static void pipe(InputStream from, Socket to) {
		try {
			pipe(from, to.getOutputStream());
		} catch (IOException ignored) {
			// closed
		}
	}

	private static void pipe(InputStream from, OutputStream to) {
		byte[] buffer = new byte[8192];
		try {
			int read;
			while ((read = from.read(buffer)) >= 0) {
				to.write(buffer, 0, read);
				to.flush();
			}
		} catch (IOException ignored) {
			// closed
		}
	}

	@Override
	public void close() throws IOException {
		this.running = false;
		this.server.close();
	}
}
