package dev.wisteria.modules.client;

import dev.wisteria.setting.ActionSetting;
import dev.wisteria.setting.BoolSetting;
import dev.wisteria.setting.InfoSetting;
import dev.wisteria.setting.InputSetting;
import dev.wisteria.setting.KeySetting;
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
	private final KeySetting panicKey = this.add(new KeySetting("Panic Key",
		"Hides the whole client at once: every module off, nothing drawn or printed. Press again to restore"));
	private final BoolSetting panicLeaves = this.add(new BoolSetting("Panic Leaves World", "The panic also leaves the world or server, the game's own way",
		false));
	private final BoolSetting panicQuits = this.add(new BoolSetting("Panic Quits Game", "The panic also closes the game, the game's own way", false));

	public PrivacySection() {
		super("Privacy", "Brand, mod channels, chat guard, network audit");
		this.add(new ListSetting("Guard Words", "Whole words a chat line may not contain", "word",
			() -> this.client().privacy().words(), word -> this.client().privacy().addWord(word), word -> this.client().privacy().removeWord(word)));
		this.add(new InfoSetting("Guard Hits", "Lines held back since the game started", () -> Integer.toString(this.client().privacy().guardCount())));
		this.add(new ActionSetting("Panic", "Hide the client now (the Panic Key does the same)", "Panic",
			() -> this.client().panic().trigger(this.panicLeaves.get(), this.panicQuits.get()))
			.withNote("hides the client and closes this screen; covered by PanicGameTest"));
		this.add(new ActionSetting("Restore", "Bring back the modules that were on before the panic", "Restore", () -> this.client().panic().restore()));
		this.add(new InfoSetting("Panic State", "Whether the panic is on, and how many modules Restore brings back",
			() -> this.client().panic().active() ? "on: " + this.client().panic().remembered().size() + " to restore" : "off"));
		this.add(new InputSetting("Clear My Data",
			"Deletes everything in the client's data folder: secrets, proxies, seeds, BaseFinder lists, profiles. The folder and the mod stay. Type DELETE",
			"type DELETE to confirm", "Clear", text -> {
				if (!text.strip().equals("DELETE")) {
					throw new IllegalArgumentException("Type DELETE, in capitals, to confirm");
				}

				int deleted = this.client().clearMyData();
				this.client().platform().chat().print("Cleared: " + deleted + " files deleted from the client's data folder");
			}));
		this.add(new ActionSetting("Network Audit", "Writes everything sent to servers this session to a file and opens its folder", "Write",
			() -> this.client().platform().writeNetworkAudit()).withNote("writes a file and opens a folder; covered by its own test"));
	}

	public String brand() {
		return this.brand.get();
	}

	public boolean hideChannels() {
		return this.hideChannels.get();
	}

	public KeySetting panicKey() {
		return this.panicKey;
	}

	public boolean panicLeavesWorld() {
		return this.panicLeaves.get();
	}

	public boolean panicQuitsGame() {
		return this.panicQuits.get();
	}

	public boolean guard() {
		return this.guard.get();
	}
}
