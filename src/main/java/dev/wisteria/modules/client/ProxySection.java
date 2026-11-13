package dev.wisteria.modules.client;

import dev.wisteria.module.Category;
import dev.wisteria.module.Module;
import dev.wisteria.proxy.ProxyConfig;
import dev.wisteria.proxy.ProxyManager;
import dev.wisteria.setting.ActionSetting;
import dev.wisteria.setting.ChoiceSetting;
import dev.wisteria.setting.EnumSetting;
import dev.wisteria.setting.ListSetting;
import dev.wisteria.setting.PasswordSetting;
import dev.wisteria.setting.StringSetting;
import java.util.Optional;

/**
 * The proxy for this profile. On means every connection to a server goes through the chosen proxy, with the server's
 * name resolved by the proxy; if it does not answer, the connection is blocked rather than made directly.
 */
public final class ProxySection extends Module {
	public enum Packs {
		REJECT, ACCEPT
	}

	private final ChoiceSetting proxy = this.add(new ChoiceSetting("Proxy", "Which proxy this profile connects through", "",
		() -> this.client().proxies().names(), true));
	private final ListSetting proxies = this.add(new ListSetting("Proxies", "Add as: name socks5|http host:port", "name socks5 host:port",
		() -> this.client().proxies().proxies().stream().map(ProxyConfig::describe).toList(),
		text -> this.client().proxies().add(ProxyConfig.parse(text)),
		entry -> this.client().proxies().remove(entry.split(" ")[0])));
	private final PasswordSetting login = this.add(new PasswordSetting("Login", "Proxy login for the chosen proxy; stored in .env, never shown",
		() -> this.chosen().map(chosen -> this.client().secrets().forCore(chosen.userKey()).isPresent()).orElse(false),
		value -> this.client().secrets().put(this.require().userKey(), value)));
	private final PasswordSetting password = this.add(new PasswordSetting("Password", "Proxy password for the chosen proxy; stored in .env, never shown",
		() -> this.chosen().map(chosen -> this.client().secrets().forCore(chosen.passwordKey()).isPresent()).orElse(false),
		value -> this.client().secrets().put(this.require().passwordKey(), value)));
	private final StringSetting ipService = this.add(new StringSetting("IP Service", "Host Test asks for your address; allow it under Network", "api.ipify.org", 64));
	private final ActionSetting test = this.add(new ActionSetting("Test", "Asks the IP service, through the proxy, which address the world sees", "Test",
		() -> this.client().proxies().test(this.ipService.get().strip())).withNote("sends a request out through the proxy; covered by its own test"));
	private final EnumSetting<Packs> packs = this.add(new EnumSetting<>("Server Packs",
		"With a proxy on: refuse server resource packs (their host would see your real address), or accept them", Packs.REJECT));

	public ProxySection() {
		super("Proxy", "Connect to servers through a proxy; blocks the connection if it does not answer", Category.CLIENT);
	}

	@Override
	protected boolean remembersEnabled() {
		return true;
	}

	private Optional<ProxyConfig> chosen() {
		return this.client().proxies().byName(this.proxy.get());
	}

	private ProxyConfig require() {
		return this.chosen().orElseThrow(() -> new IllegalArgumentException("Choose a proxy first"));
	}

	/** What the next connection does, for the proxy manager. Any thread. */
	public ProxyManager.Route route() {
		if (!this.isEnabled()) {
			return new ProxyManager.Route.Direct();
		}

		if (this.proxy.get().isEmpty()) {
			return new ProxyManager.Route.Blocked("the proxy is on but none is chosen; nothing was connected");
		}

		Optional<ProxyConfig> chosen = this.chosen();
		return chosen.isPresent() ? this.client().proxies().via(chosen.get())
			: new ProxyManager.Route.Blocked("proxy " + this.proxy.get() + " no longer exists; nothing was connected");
	}

	public Packs packs() {
		return this.packs.get();
	}
}
