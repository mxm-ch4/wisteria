package dev.wisteria.gate.mixin;

import dev.wisteria.gate.AuditLog;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The test server's side of every connection: records what arrives from the client, for the network audit. */
@Mixin(Connection.class)
abstract class ServerReceiveMixin {
	@Shadow
	public abstract PacketFlow getReceiving();

	@Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
	private void wisteriaGate$record(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
		if (this.getReceiving() == PacketFlow.SERVERBOUND) {
			AuditLog.record(packet);
		}
	}
}
