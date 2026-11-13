package dev.wisteria.modules.client;

import dev.wisteria.setting.ActionSetting;
import dev.wisteria.setting.BoolSetting;
import dev.wisteria.setting.InfoSetting;
import dev.wisteria.setting.ListSetting;
import dev.wisteria.setting.StringSetting;

/**
 * What the client gives away about itself on a server: the brand it reports, the mod's channel list, and the guard
 * that holds back chat lines that would leak a secret, the proxy, or the client's name. Plus a record of everything
 * sent this session, written to a file on request.
 */
public final class PrivacySection extends ClientSection {
	private final StringSetting brand = this.add(new StringSetting("Client Brand", "What the server is told this client is; the game's own is \"vanilla\"", "vanilla", 32));
	private final BoolSetting hideChannels = this.add(new BoolSetting("Hide Mod Channels",
		"Announce no mod channels and send no mod data; turn off only for a server that needs a mod channel", true));
	private final BoolSetting guard = this.add(new BoolSetting("Chat Guard", "Hold back chat that contains a secret, your proxy, or a guard word", true));

	public PrivacySection() {
		super("Privacy", "Brand, mod channels, chat guard, network audit");
		this.add(new ListSetting("Guard Words", "Whole words a chat line may not contain", "word",
			() -> this.client().privacy().words(), word -> this.client().privacy().addWord(word), word -> this.client().privacy().removeWord(word)));
		this.add(new InfoSetting("Guard Hits", "Lines held back since the game started", () -> Integer.toString(this.client().privacy().guardCount())));
		this.add(new ActionSetting("Network Audit", "Writes everything sent to servers this session to a file and opens its folder", "Write",
			() -> this.client().platform().writeNetworkAudit()).withNote("writes a file and opens a folder; covered by its own test"));
	}

	public String brand() {
		return this.brand.get();
	}

	public boolean hideChannels() {
		return this.hideChannels.get();
	}

	public boolean guard() {
		return this.guard.get();
	}
}
