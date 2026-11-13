package dev.wisteria.platform.game;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;

/**
 * Everything this client sent to servers since the game started, one line per kind of packet with the fields that
 * could identify the client (brand, channels, settings), without the ones that change all the time. Kept in memory,
 * written to a file only when the user asks (Client → Privacy → Network Audit).
 */
public final class SessionAudit {
	private static final Map<String, Integer> LINES = new TreeMap<>();

	private SessionAudit() {
	}

	public static void record(Packet<?> packet) {
		String line = describe(packet);
		synchronized (LINES) {
			LINES.merge(line, 1, Integer::sum);
		}
	}

	/** One packet as the audit lists it; shared with the gate's server-side audit. */
	public static String describe(Packet<?> packet) {
		String type = packet.type().id().toString();
		return switch (packet) {
			case ServerboundCustomPayloadPacket custom when custom.payload() instanceof BrandPayload brand -> type + " minecraft:brand \"" + brand.brand() + "\"";
			case ServerboundCustomPayloadPacket custom -> type + " " + custom.payload().type().id() + " " + summarise(custom.payload().toString());
			case ServerboundClientInformationPacket information -> type + " " + information.information();
			case ClientIntentionPacket intention -> type + " protocol " + intention.protocolVersion() + " intent " + intention.intention();
			case ServerboundHelloPacket hello -> type + " name " + hello.name();
			case ServerboundChatPacket chat -> type + " (a chat message)";
			case ServerboundChatCommandPacket command -> type + " (a command)";
			case ServerboundChatCommandSignedPacket command -> type + " (a signed command)";
			default -> type;
		};
	}

	private static String summarise(String text) {
		String clean = text.replaceAll("@[0-9a-f]{4,}", "");
		return clean.length() > 400 ? clean.substring(0, 400) + "…" : clean;
	}

	/** Writes the record to .minecraft/wisteria/audit/network-&lt;time&gt;.txt. */
	static Path write(Path wisteriaRoot) throws IOException {
		List<String> out = new ArrayList<>();
		out.add("# Everything this client sent to servers since the game started: packet kind, identifying fields, count.");
		synchronized (LINES) {
			LINES.forEach((line, count) -> out.add(line + "\t×" + count));
		}

		Path dir = wisteriaRoot.resolve("audit");
		Files.createDirectories(dir);
		Path file = dir.resolve("network-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
		Files.write(file, out, StandardCharsets.UTF_8);
		return file;
	}
}
