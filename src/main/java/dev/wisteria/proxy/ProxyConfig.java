package dev.wisteria.proxy;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One proxy the user configured: a name, SOCKS5 or HTTP CONNECT, host and port. Login and password are not here:
 * they live in the secrets file under {@link #userKey()} and {@link #passwordKey()}.
 */
public record ProxyConfig(String name, Type type, String host, int port) {
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,24}");
	private static final Pattern HOST = Pattern.compile("[A-Za-z0-9.-]{1,253}|\\[[0-9A-Fa-f:.]+]");

	public enum Type {
		SOCKS5, HTTP;

		public String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	/** "work socks5 10.0.0.2:1080": what the GUI field and the command take. */
	public static ProxyConfig parse(String text) {
		String[] parts = text.strip().split("\\s+");
		if (parts.length != 3) {
			throw new IllegalArgumentException("Write it as: name socks5|http host:port");
		}

		if (!NAME.matcher(parts[0]).matches()) {
			throw new IllegalArgumentException("A name is letters, digits, - and _");
		}

		Type type = switch (parts[1].toLowerCase(Locale.ROOT)) {
			case "socks5", "socks" -> Type.SOCKS5;
			case "http", "https" -> Type.HTTP;
			default -> throw new IllegalArgumentException("Type is socks5 or http, not \"" + parts[1] + "\"");
		};
		int colon = parts[2].lastIndexOf(':');
		if (colon <= 0 || colon == parts[2].length() - 1) {
			throw new IllegalArgumentException("Address is host:port");
		}

		String host = parts[2].substring(0, colon);
		if (!HOST.matcher(host).matches()) {
			throw new IllegalArgumentException("\"" + host + "\" is not a host name or address");
		}

		int port;
		try {
			port = Integer.parseInt(parts[2].substring(colon + 1));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Port must be a number");
		}

		if (port < 1 || port > 65535) {
			throw new IllegalArgumentException("Port must be from 1 to 65535");
		}

		return new ProxyConfig(parts[0], type, host, port);
	}

	/** "work — socks5 10.0.0.2:1080". */
	public String describe() {
		return this.name + " — " + this.type.id() + " " + this.address();
	}

	public String address() {
		return this.host + ":" + this.port;
	}

	/** Key of the login in the secrets file: one per proxy, so proxies never share credentials. */
	public String userKey() {
		return "PROXY_" + this.name.toUpperCase(Locale.ROOT).replace('-', '_') + "_USER";
	}

	public String passwordKey() {
		return "PROXY_" + this.name.toUpperCase(Locale.ROOT).replace('-', '_') + "_PASSWORD";
	}
}
