package dev.wisteria.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Against a server on this machine: the only place plain http is allowed. */
class NetTest {
	private static final String TOKEN = "123456:AAH-made-up-token-for-tests";

	@TempDir
	Path root;
	private HttpServer server;
	private final List<String> requests = new CopyOnWriteArrayList<>();
	private final AtomicInteger failuresLeft = new AtomicInteger();
	private final AtomicLong clock = new AtomicLong(1_000L);
	private Net net;
	private Secrets secrets;
	private String base;

	@BeforeEach
	void start() throws Exception {
		Files.writeString(this.root.resolve(".env"), "TELEGRAM_BOT_TOKEN=" + TOKEN + "\n");
		this.secrets = new Secrets(this.root);
		this.net = new Net(this.secrets, this.clock::get);
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.createContext("/", exchange -> {
			this.requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " auth=" + exchange.getRequestHeaders().getFirst("Authorization")
				+ " body=" + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			if (exchange.getRequestURI().getPath().equals("/slow")) {
				try {
					Thread.sleep(1500);
				} catch (InterruptedException ignored) {
					Thread.currentThread().interrupt();
				}
			}

			int status = this.failuresLeft.getAndDecrement() > 0 ? 503 : exchange.getRequestURI().getPath().equals("/missing") ? 404 : 200;
			byte[] body = ("status " + status).getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		this.server.start();
		this.base = "http://127.0.0.1:" + this.server.getAddress().getPort();
	}

	@AfterEach
	void stop() {
		this.server.stop(0);
		this.net.close();
	}

	private Throwable failure(Net.Request request) {
		CompletionException error = assertThrows(CompletionException.class, () -> this.net.send("notifier", request).join());
		return error.getCause();
	}

	@Test
	void modulesWithoutPermissionGetNoNetwork() {
		this.net.allow("127.0.0.1");
		assertThrows(SecurityException.class, () -> this.net.send("nuker", Net.Request.get(this.base + "/")));
		assertTrue(this.requests.isEmpty());
	}

	@Test
	void onlyHostsTheUserAllowed() {
		Throwable refused = this.failure(Net.Request.get(this.base + "/"));
		assertInstanceOf(Net.NetException.class, refused);
		assertTrue(refused.getMessage().contains("not allowed"), refused.getMessage());
		assertTrue(this.requests.isEmpty());

		this.net.allow("127.0.0.1");
		assertEquals(200, this.net.send("notifier", Net.Request.get(this.base + "/")).join().status());
		assertTrue(this.net.deny("127.0.0.1"));
		assertInstanceOf(Net.NetException.class, this.failure(Net.Request.get(this.base + "/")));
	}

	@Test
	void plainHttpOnlyToThisMachine() {
		this.net.allow("example.com");
		Throwable refused = this.failure(Net.Request.get("http://example.com/"));
		assertTrue(refused.getMessage().contains("only https"), refused.getMessage());
	}

	@Test
	void secretsGoIntoTheRequestButNeverIntoMessages() {
		this.net.allow("127.0.0.1");
		Secret token = this.secrets.get("notifier", "TELEGRAM_BOT_TOKEN").orElseThrow();
		Net.Request request = Net.Request.post(this.base + "/bot{token}/sendMessage").secret("token", token)
			.header("Authorization", "Bearer {token}").json("{\"text\":\"hi\"}");
		Net.Response response = this.net.send("notifier", request).join();
		assertTrue(response.ok());
		assertEquals("POST /bot" + TOKEN + "/sendMessage auth=Bearer " + TOKEN + " body={\"text\":\"hi\"}", this.requests.getFirst());
		assertFalse(request.toString().contains(TOKEN));

		this.failuresLeft.set(100);
		Throwable failed = this.failure(Net.Request.get(this.base + "/bot{token}/x").secret("token", token).retries(0));
		assertFalse(failed.getMessage().contains(TOKEN), failed.getMessage());
		assertTrue(failed.getMessage().contains("{token}"), failed.getMessage());
	}

	@Test
	void retriesServerErrorsButNotClientErrors() {
		this.net.allow("127.0.0.1");
		this.failuresLeft.set(2);
		assertEquals(200, this.net.send("notifier", Net.Request.get(this.base + "/")).join().status());
		assertEquals(3, this.requests.size(), "two failures, then the answer");

		this.requests.clear();
		assertEquals(404, this.net.send("notifier", Net.Request.get(this.base + "/missing")).join().status());
		assertEquals(1, this.requests.size(), "a 404 is an answer, not a reason to try again");
	}

	@Test
	void timesOut() {
		this.net.allow("127.0.0.1");
		Throwable failed = this.failure(Net.Request.get(this.base + "/slow").timeout(Duration.ofMillis(200)).retries(0));
		assertTrue(failed.getMessage().contains("Timeout"), failed.getMessage());
	}

	@Test
	void suspendsAModuleAfterRepeatedFailuresAndComesBackLater() {
		this.net.allow("127.0.0.1");
		List<String> told = new CopyOnWriteArrayList<>();
		this.net.onSuspend((module, reason) -> told.add(module + ": " + reason));
		this.failuresLeft.set(1000);
		for (int i = 0; i < Net.FAILURES_TO_SUSPEND; i++) {
			this.failure(Net.Request.get(this.base + "/").retries(0));
		}

		assertEquals(List.of("notifier: network suspended for 5 minutes after 3 failed requests"), told);
		int sent = this.requests.size();
		Throwable suspended = this.failure(Net.Request.get(this.base + "/"));
		assertTrue(suspended.getMessage().contains("suspended"), suspended.getMessage());
		assertEquals(sent, this.requests.size(), "nothing is sent while suspended");

		this.clock.addAndGet(Duration.ofMinutes(Net.SUSPEND_MINUTES).toNanos());
		this.failuresLeft.set(0);
		assertEquals(200, this.net.send("notifier", Net.Request.get(this.base + "/")).join().status());
	}

	@Test
	void acceptsOnlyHostNames() {
		assertTrue(this.net.allow("API.Telegram.org"));
		assertFalse(this.net.allow("api.telegram.org"), "already there, in lower case");
		assertFalse(this.net.allow("https://evil.example/path"));
		assertFalse(this.net.allow("*.example.com"));
		assertEquals(List.of("api.telegram.org"), this.net.allowedHosts());
	}
}
