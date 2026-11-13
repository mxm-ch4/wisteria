package dev.wisteria.io;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The only way out to the internet. A request is sent only if the module has {@link Permission#NET} and the host is
 * one the user allowed ({@code .net allow api.telegram.org}); only HTTPS, except to this machine. Each request has
 * a timeout and is retried on network errors and 5xx/429 answers; after {@value #FAILURES_TO_SUSPEND} failed
 * requests in a row a module's network is suspended for {@value #SUSPEND_MINUTES} minutes. Requests run on
 * Wisteria's own threads, never the game's: hop back with {@code thenAcceptAsync(..., platform()::execute)}.
 */
public final class Net {
	public static final int FAILURES_TO_SUSPEND = 3;
	public static final int SUSPEND_MINUTES = 5;
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
	private static final Pattern HOST = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*");
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)}");
	private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

	private final Secrets secrets;
	private final LongSupplier clock;
	private final Set<String> allowed = ConcurrentHashMap.newKeySet();
	private final Map<String, Health> health = new ConcurrentHashMap<>();
	private final java.util.concurrent.atomic.AtomicLong requests = new java.util.concurrent.atomic.AtomicLong();
	private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
	private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
		Thread thread = new Thread(runnable, "Wisteria Net");
		thread.setDaemon(true);
		return thread;
	});
	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(CONNECT_TIMEOUT)
		.followRedirects(HttpClient.Redirect.NEVER)
		.executor(this.executor)
		.build();
	private volatile @Nullable BiConsumer<String, String> suspendListener;
	private volatile java.util.function.Supplier<@Nullable String> offline = () -> null;

	public Net(Secrets secrets) {
		this(secrets, System::nanoTime);
	}

	/** @param clock nanoseconds, for tests */
	public Net(Secrets secrets, LongSupplier clock) {
		this.secrets = secrets;
		this.clock = clock;
	}

	// ---- the user's allow list ----

	public static @Nullable String normalizeHost(String host) {
		String lower = host.strip().toLowerCase(Locale.ROOT);
		return HOST.matcher(lower).matches() && lower.length() <= 253 ? lower : null;
	}

	/** @return false if the name is not a host name or it was already allowed */
	public boolean allow(String host) {
		String normalized = normalizeHost(host);
		if (normalized == null || !this.allowed.add(normalized)) {
			return false;
		}

		this.changed();
		return true;
	}

	public boolean deny(String host) {
		String normalized = normalizeHost(host);
		if (normalized == null || !this.allowed.remove(normalized)) {
			return false;
		}

		this.changed();
		return true;
	}

	public List<String> allowedHosts() {
		return List.copyOf(new TreeSet<>(this.allowed));
	}

	public boolean isAllowed(String host) {
		return this.allowed.contains(host.toLowerCase(Locale.ROOT));
	}

	/** Replaces the list, from the config; does not count as a change. */
	public void load(List<String> hosts) {
		this.allowed.clear();
		for (String host : hosts) {
			String normalized = normalizeHost(host);
			if (normalized != null) {
				this.allowed.add(normalized);
			}
		}
	}

	public void onChange(Runnable listener) {
		this.changeListeners.add(listener);
	}

	/**
	 * Why the client's own network is off right now (a proxy is on for this profile, and requests would leave from the
	 * real address), or null when it is on.
	 */
	public void offlineWhen(java.util.function.Supplier<@Nullable String> reason) {
		this.offline = reason;
	}

	/** Told (module id, reason) when a module's network is suspended; the client prints it in chat. */
	public void onSuspend(BiConsumer<String, String> listener) {
		this.suspendListener = listener;
	}

	private void changed() {
		this.changeListeners.forEach(Runnable::run);
	}

	// ---- requests ----

	/** @return null if the module's network is working, otherwise why it is not */
	public @Nullable String suspendedReason(String moduleId) {
		Health state = this.health.get(moduleId);
		if (state == null || state.suspendedUntil == 0L) {
			return null;
		}

		if (this.clock.getAsLong() - state.suspendedUntil >= 0L) {
			state.suspendedUntil = 0L;
			state.failures = 0;
			return null;
		}

		return "suspended after " + FAILURES_TO_SUSPEND + " failed requests in a row";
	}

	/**
	 * Sends a request for a module. The future fails with {@link NetException} (whose message is safe to show)
	 * when the request is refused or every attempt failed; an HTTP error status that is not retried is a normal
	 * response.
	 *
	 * @throws SecurityException at once when the module may not use the network
	 */
	public CompletableFuture<Response> send(String moduleId, Request request) {
		Permission.NET.check(moduleId);
		String off = this.offline.get();
		if (off != null) {
			return CompletableFuture.failedFuture(new NetException(moduleId + ": network off: " + off));
		}

		String suspended = this.suspendedReason(moduleId);
		if (suspended != null) {
			return CompletableFuture.failedFuture(new NetException(moduleId + ": network " + suspended));
		}

		URI uri;
		try {
			uri = this.resolve(request);
		} catch (NetException e) {
			return CompletableFuture.failedFuture(e);
		}

		HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(request.timeout);
		for (Map.Entry<String, String> header : request.headers.entrySet()) {
			builder.header(header.getKey(), this.fill(header.getValue(), request.secrets));
		}

		HttpRequest built = builder.method(request.method, request.body == null
			? HttpRequest.BodyPublishers.noBody()
			: HttpRequest.BodyPublishers.ofString(this.fill(request.body, request.secrets), StandardCharsets.UTF_8)).build();
		CompletableFuture<Response> result = new CompletableFuture<>();
		this.attempt(moduleId, request, built, 0, result);
		return result;
	}

	/** Requests that actually left the machine since the game started (retries included). */
	public long requestsMade() {
		return this.requests.get();
	}

	private void attempt(String moduleId, Request request, HttpRequest built, int attempt, CompletableFuture<Response> result) {
		this.requests.incrementAndGet();
		this.http.sendAsync(built, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).whenComplete((response, error) -> {
			boolean retryable = error != null || response.statusCode() == 429 || response.statusCode() >= 500;
			if (retryable && attempt < request.retries) {
				long delay = 250L << attempt;
				CompletableFuture.delayedExecutor(delay, java.util.concurrent.TimeUnit.MILLISECONDS, this.executor)
					.execute(() -> this.attempt(moduleId, request, built, attempt + 1, result));
				return;
			}

			String where = request.method + " " + request.template;
			if (retryable) {
				this.failed(moduleId);
				String reason = error != null ? error.getClass().getSimpleName() + (error.getMessage() != null ? ": " + error.getMessage() : "")
					: "HTTP " + response.statusCode();
				result.completeExceptionally(new NetException(this.secrets.redact(where + " failed after " + (attempt + 1) + " attempts: " + reason)));
			} else {
				this.health.remove(moduleId);
				result.complete(new Response(response.statusCode(), response.body()));
			}
		});
	}

	private void failed(String moduleId) {
		Health state = this.health.computeIfAbsent(moduleId, id -> new Health());
		synchronized (state) {
			state.failures++;
			if (state.failures >= FAILURES_TO_SUSPEND && state.suspendedUntil == 0L) {
				long until = this.clock.getAsLong() + Duration.ofMinutes(SUSPEND_MINUTES).toNanos();
				state.suspendedUntil = until == 0L ? 1L : until;
				BiConsumer<String, String> listener = this.suspendListener;
				if (listener != null) {
					listener.accept(moduleId, "network suspended for " + SUSPEND_MINUTES + " minutes after " + FAILURES_TO_SUSPEND + " failed requests");
				}
			}
		}
	}

	private URI resolve(Request request) throws NetException {
		URI uri;
		try {
			uri = new URI(this.fill(request.template, request.secrets));
		} catch (URISyntaxException e) {
			throw new NetException(request.template + " is not a valid address");
		}

		String host = uri.getHost();
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (host == null) {
			throw new NetException(request.template + " has no host");
		}

		host = host.toLowerCase(Locale.ROOT);
		boolean loopback = LOOPBACK.contains(host);
		if (!scheme.equals("https") && !(scheme.equals("http") && loopback)) {
			throw new NetException(this.secrets.redact(host) + ": only https is allowed");
		}

		if (!this.isAllowed(host)) {
			throw new NetException(this.secrets.redact(host) + " is not allowed; the user can allow it with .net allow " + this.secrets.redact(host));
		}

		return uri;
	}

	private String fill(String template, Map<String, Secret> secrets) {
		Matcher matcher = PLACEHOLDER.matcher(template);
		StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			Secret secret = secrets.get(matcher.group(1));
			matcher.appendReplacement(out, Matcher.quoteReplacement(secret != null ? secret.value() : matcher.group()));
		}

		matcher.appendTail(out);
		return out.toString();
	}

	/** Stops the threads; on game shutdown. */
	public void close() {
		this.executor.shutdownNow();
	}

	private static final class Health {
		int failures;
		long suspendedUntil;
	}

	/**
	 * What to send. {@code {name}} in the address, a header or the body is replaced by the secret given with
	 * {@link #secret} at the moment of sending; logs and errors only ever show the template.
	 */
	public static final class Request {
		private final String method;
		private final String template;
		private final Map<String, String> headers = new LinkedHashMap<>();
		private final Map<String, Secret> secrets = new LinkedHashMap<>();
		private @Nullable String body;
		private Duration timeout = Duration.ofSeconds(10);
		private int retries = 2;

		private Request(String method, String template) {
			this.method = method;
			this.template = template;
		}

		public static Request get(String url) {
			return new Request("GET", url);
		}

		public static Request post(String url) {
			return new Request("POST", url);
		}

		public Request header(String name, String value) {
			this.headers.put(name, value);
			return this;
		}

		public Request secret(String placeholder, Secret secret) {
			this.secrets.put(placeholder, secret);
			return this;
		}

		public Request json(String json) {
			this.headers.put("Content-Type", "application/json");
			this.body = json;
			return this;
		}

		public Request form(String body) {
			this.headers.put("Content-Type", "application/x-www-form-urlencoded");
			this.body = body;
			return this;
		}

		/** 10 seconds by default, at most 60. */
		public Request timeout(Duration timeout) {
			this.timeout = timeout.compareTo(Duration.ofSeconds(60)) > 0 ? Duration.ofSeconds(60) : timeout;
			return this;
		}

		/** Extra attempts after the first; 2 by default, at most 5. */
		public Request retries(int retries) {
			this.retries = Math.clamp(retries, 0, 5);
			return this;
		}

		@Override
		public String toString() {
			return this.method + " " + this.template;
		}
	}

	public record Response(int status, String body) {
		public boolean ok() {
			return this.status >= 200 && this.status < 300;
		}
	}

	/** A refused or failed request. The message never contains a secret. */
	public static final class NetException extends IOException {
		public NetException(String message) {
			super(message);
		}
	}
}
