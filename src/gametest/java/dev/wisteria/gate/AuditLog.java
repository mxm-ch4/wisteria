package dev.wisteria.gate;

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
 * What the server received from the client, as the server saw it: one line per kind of packet, with the fields that
 * could tell one client from another (brand, channels, settings) and without the ones that change every session
 * (positions, times, salts). Filled on the server's network threads by the gate's Connection mixin.
 */
public final class AuditLog {
	private static final Map<String, Integer> LINES = new TreeMap<>();
	private static volatile boolean recording;
	private static int chatPackets;

	private AuditLog() {
	}

	public static synchronized void start() {
		LINES.clear();
		chatPackets = 0;
		recording = true;
	}

	public static synchronized void stop() {
		recording = false;
	}

	public static synchronized Map<String, Integer> lines() {
		return new TreeMap<>(LINES);
	}

	/** Chat messages and commands the server got, of any kind. */
	public static synchronized int chatPackets() {
		return chatPackets;
	}

	public static void record(Packet<?> packet) {
		if (!recording) {
			return;
		}

		String line = describe(packet);
		synchronized (AuditLog.class) {
			LINES.merge(line, 1, Integer::sum);
			if (packet instanceof ServerboundChatPacket || packet instanceof ServerboundChatCommandPacket || packet instanceof ServerboundChatCommandSignedPacket) {
				chatPackets++;
			}
		}
	}

	static String describe(Packet<?> packet) {
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

	// Payload records print their fields; keep it short and drop object hashes.
	private static String summarise(String text) {
		String clean = text.replaceAll("@[0-9a-f]{4,}", "");
		return clean.length() > 400 ? clean.substring(0, 400) + "…" : clean;
	}
}
