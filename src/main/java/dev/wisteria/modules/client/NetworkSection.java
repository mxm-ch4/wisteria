package dev.wisteria.modules.client;

import dev.wisteria.io.Net;
import dev.wisteria.setting.ListSetting;

/** The hosts the client may reach (Notifier, the proxy test). Nothing else ever connects out. */
public final class NetworkSection extends ClientSection {
	public NetworkSection() {
		super("Network", "Hosts the client may connect to");
		this.add(new ListSetting("Allowed Hosts", "Add a host name, e.g. api.telegram.org", "host name",
			() -> this.client().net().allowedHosts(),
			host -> {
				if (Net.normalizeHost(host) == null) {
					throw new IllegalArgumentException("\"" + host + "\" is not a host name");
				}

				this.client().net().allow(host);
			},
			host -> this.client().net().deny(host)));
	}
}
