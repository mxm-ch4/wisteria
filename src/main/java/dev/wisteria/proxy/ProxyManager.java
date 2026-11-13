package dev.wisteria.proxy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.wisteria.Client;
import dev.wisteria.Wisteria;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.jspecify.annotations.Nullable;

/**
 * The proxies the user configured (kept in client.json) and the route the game's connections take for the active
 * profile. Leak-proof by design: once a profile asks for a proxy, connections go through it or not at all.
 */
public final class ProxyManager {
	/** How a connection to a server is made right now. */
	public sealed interface Route {
		/** No proxy: the game connects as usual. */
		record Direct() implements Route {
		}

		/** Through this proxy, the server's name resolved by the proxy. */
		record Via(ProxyConfig proxy, @Nullable String user, @Nullable String password) implements Route {
			@Override
			public String toString() {
				return "Via[" + this.proxy.describe() + (this.user != null ? ", with login" : "") + "]";
			}
		}

		/** A proxy is wanted but cannot be used (none chosen, or it was removed): nothing connects. */
		record Blocked(String reason) implements Route {
		}
	}

	private final Client client;
	private final List<ProxyConfig> proxies = new CopyOnWriteArrayList<>();
	private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
	private volatile Supplier<Route> route = Route.Direct::new;

	public ProxyManager(Client client) {
		this.client = client;
	}

	public List<ProxyConfig> proxies() {
		return List.copyOf(this.proxies);
	}

	public List<String> names() {
		return this.proxies.stream().map(ProxyConfig::name).toList();
	}

	public Optional<ProxyConfig> byName(String name) {
		return this.proxies.stream().filter(proxy -> proxy.name().equalsIgnoreCase(name)).findFirst();
	}

	/** @throws IllegalArgumentException if the name is taken */
	public void add(ProxyConfig proxy) {
		if (this.byName(proxy.name()).isPresent()) {
			throw new IllegalArgumentException("There is already a proxy called " + proxy.name());
		}

		this.proxies.add(proxy);
		this.changed();
	}

	/** Also forgets its login and password. */
	public boolean remove(String name) {
		Optional<ProxyConfig> proxy = this.byName(name);
		if (proxy.isEmpty()) {
			return false;
		}

		this.proxies.remove(proxy.get());
		this.client.secrets().put(proxy.get().userKey(), "");
		this.client.secrets().put(proxy.get().passwordKey(), "");
		this.changed();
		return true;
	}

	/** Set by the Proxy section: what the active profile asks for. */
	public void routeFrom(Supplier<Route> route) {
		this.route = route;
	}

	/** The route for the next connection. Any thread. */
	public Route route() {
		return this.route.get();
	}

	/** Proxy and credentials as a route; credentials come from the secrets file. */
	public Route via(ProxyConfig proxy) {
		String user = this.client.secrets().forCore(proxy.userKey()).orElse(null);
		String password = this.client.secrets().forCore(proxy.passwordKey()).orElse(null);
		return new Route.Via(proxy, user, password);
	}

	/**
	 * Asks a "what is my IP" service, through the chosen proxy, which address the world sees. Only on the user's
	 * request and only to a host they allowed under Network. Runs off the client thread; the answer goes to chat.
	 */
	public void test(String serviceHost) {
		Route route = this.route();
		if (!(route instanceof Route.Via via)) {
			this.say(route instanceof Route.Blocked blocked ? "Proxy test: " + blocked.reason() : "Proxy test: no proxy is on for this profile");
			return;
		}

		if (!this.client.net().isAllowed(serviceHost)) {
			this.say("Proxy test: allow " + serviceHost + " under Client → Network first; nothing was sent");
			return;
		}

		Thread thread = new Thread(() -> {
			try {
				String answer = fetch(via, serviceHost);
				this.say("Proxy " + via.proxy().name() + ": the world sees you as " + answer);
			} catch (IOException | RuntimeException e) {
				this.say("Proxy " + via.proxy().name() + " test failed: " + this.client.secrets().redact(String.valueOf(e.getMessage())));
			}
		}, "Wisteria proxy test");
		thread.setDaemon(true);
		thread.start();
	}

	// HTTPS GET / through the tunnel; the body, trimmed to one short line.
	static String fetch(Route.Via via, String host) throws IOException {
		try (Socket tunnel = ProxyTunnel.open(via.proxy(), via.user(), via.password(), host, 443, 10_000);
			SSLSocket tls = (SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(tunnel, host, 443, true)) {
			tls.setSoTimeout(10_000);
			tls.startHandshake();
			String request = "GET / HTTP/1.1\r\nHost: " + host + "\r\nUser-Agent: Wisteria\r\nConnection: close\r\n\r\n";
			tls.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
			tls.getOutputStream().flush();
			InputStream in = tls.getInputStream();
			ByteArrayOutputStream body = new ByteArrayOutputStream();
			in.transferTo(body);
			String response = body.toString(StandardCharsets.UTF_8);
			int start = response.indexOf("\r\n\r\n");
			String text = (start < 0 ? response : response.substring(start + 4)).strip();
			if (!response.startsWith("HTTP/1.1 200") && !response.startsWith("HTTP/1.0 200")) {
				throw new IOException(host + " answered " + response.lines().findFirst().orElse("nothing"));
			}

			// Chunked bodies: the address is the line that looks like one.
			return text.lines().filter(line -> line.matches("[0-9A-Fa-f.:]{3,45}")).reduce((first, second) -> second).orElse(text.length() > 60 ? text.substring(0, 60) : text);
		}
	}

	private void say(String message) {
		this.client.platform().execute(() -> this.client.platform().chat().print(message));
		Wisteria.LOG.info("{}", message);
	}

	// ---- persistence (client.json "proxies") ----

	public void onChange(Runnable listener) {
		this.changeListeners.add(listener);
	}

	private void changed() {
		this.changeListeners.forEach(Runnable::run);
	}

	public JsonArray toJson() {
		JsonArray array = new JsonArray();
		for (ProxyConfig proxy : this.proxies) {
			JsonObject json = new JsonObject();
			json.addProperty("name", proxy.name());
			json.addProperty("type", proxy.type().id());
			json.addProperty("host", proxy.host());
			json.addProperty("port", proxy.port());
			array.add(json);
		}

		return array;
	}

	public void load(JsonArray array) {
		List<ProxyConfig> loaded = new ArrayList<>();
		for (JsonElement element : array) {
			try {
				JsonObject json = element.getAsJsonObject();
				loaded.add(ProxyConfig.parse(json.get("name").getAsString() + " " + json.get("type").getAsString() + " "
					+ json.get("host").getAsString() + ":" + json.get("port").getAsInt()));
			} catch (RuntimeException ignored) {
				// a hand-edited entry that makes no sense is dropped
			}
		}

		this.proxies.clear();
		this.proxies.addAll(loaded);
	}
}
