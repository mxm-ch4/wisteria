package dev.wisteria.proxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.jspecify.annotations.Nullable;

/**
 * Opens a TCP tunnel through a proxy with plain sockets: the SOCKS5 handshake (with login if given) or an HTTP CONNECT.
 * The destination host name is sent to the proxy as a name, so the proxy resolves it, not this machine. Used for the
 * "test the proxy" request; the game's own connection goes through Netty's proxy handlers.
 */
public final class ProxyTunnel {
	private ProxyTunnel() {
	}

	public static Socket open(ProxyConfig proxy, @Nullable String user, @Nullable String password, String host, int port, int timeoutMillis) throws IOException {
		Socket socket = new Socket(java.net.Proxy.NO_PROXY);
		boolean ok = false;
		try {
			socket.connect(new InetSocketAddress(proxy.host(), proxy.port()), timeoutMillis);
			socket.setSoTimeout(timeoutMillis);
			if (proxy.type() == ProxyConfig.Type.SOCKS5) {
				socks5(socket, user, password, host, port);
			} else {
				httpConnect(socket, user, password, host, port);
			}

			ok = true;
			return socket;
		} finally {
			if (!ok) {
				socket.close();
			}
		}
	}

	private static void socks5(Socket socket, @Nullable String user, @Nullable String password, String host, int port) throws IOException {
		OutputStream out = socket.getOutputStream();
		InputStream in = socket.getInputStream();
		boolean login = user != null && !user.isEmpty();
		out.write(login ? new byte[] {5, 2, 0, 2} : new byte[] {5, 1, 0});
		out.flush();
		byte[] choice = readFully(in, 2);
		if (choice[0] != 5 || (choice[1] & 0xFF) == 0xFF) {
			throw new IOException("the proxy refused every login method");
		}

		if (choice[1] == 2) {
			if (!login) {
				throw new IOException("the proxy wants a login and none is set");
			}

			byte[] name = user.getBytes(StandardCharsets.UTF_8);
			byte[] secret = (password == null ? "" : password).getBytes(StandardCharsets.UTF_8);
			ByteArrayOutputStream auth = new ByteArrayOutputStream();
			auth.write(1);
			auth.write(name.length);
			auth.write(name);
			auth.write(secret.length);
			auth.write(secret);
			out.write(auth.toByteArray());
			out.flush();
			if (readFully(in, 2)[1] != 0) {
				throw new IOException("the proxy rejected the login");
			}
		}

		byte[] target = host.getBytes(StandardCharsets.US_ASCII);
		ByteArrayOutputStream request = new ByteArrayOutputStream();
		request.write(new byte[] {5, 1, 0, 3, (byte)target.length});
		request.write(target);
		request.write(port >> 8);
		request.write(port & 0xFF);
		out.write(request.toByteArray());
		out.flush();
		byte[] reply = readFully(in, 4);
		if (reply[1] != 0) {
			throw new IOException("the proxy could not reach " + host + " (SOCKS reply " + reply[1] + ")");
		}

		int skip = switch (reply[3]) {
			case 1 -> 4;
			case 4 -> 16;
			case 3 -> readFully(in, 1)[0] & 0xFF;
			default -> throw new IOException("bad SOCKS reply");
		};
		readFully(in, skip + 2);
	}

	private static void httpConnect(Socket socket, @Nullable String user, @Nullable String password, String host, int port) throws IOException {
		StringBuilder request = new StringBuilder("CONNECT " + host + ":" + port + " HTTP/1.1\r\nHost: " + host + ":" + port + "\r\n");
		if (user != null && !user.isEmpty()) {
			String token = Base64.getEncoder().encodeToString((user + ":" + (password == null ? "" : password)).getBytes(StandardCharsets.UTF_8));
			request.append("Proxy-Authorization: Basic ").append(token).append("\r\n");
		}

		request.append("\r\n");
		socket.getOutputStream().write(request.toString().getBytes(StandardCharsets.US_ASCII));
		socket.getOutputStream().flush();
		String status = readHead(socket.getInputStream());
		if (!status.matches("HTTP/1\\.[01] 2\\d\\d.*")) {
			throw new IOException("the proxy answered " + (status.isEmpty() ? "nothing" : status));
		}
	}

	// The status line; the headers after it are read and dropped.
	private static String readHead(InputStream in) throws IOException {
		ByteArrayOutputStream head = new ByteArrayOutputStream();
		int matched = 0;
		while (matched < 4) {
			int b = in.read();
			if (b < 0) {
				break;
			}

			head.write(b);
			matched = (b == '\r' || b == '\n') ? ((matched % 2 == 0) == (b == '\r') ? matched + 1 : (b == '\r' ? 1 : 0)) : 0;
			if (head.size() > 8192) {
				throw new IOException("the proxy's answer is too long");
			}
		}

		String text = head.toString(StandardCharsets.ISO_8859_1);
		int end = text.indexOf("\r\n");
		return end < 0 ? text.strip() : text.substring(0, end);
	}

	private static byte[] readFully(InputStream in, int length) throws IOException {
		byte[] data = in.readNBytes(length);
		if (data.length != length) {
			throw new IOException("the proxy closed the connection");
		}

		return data;
	}
}
