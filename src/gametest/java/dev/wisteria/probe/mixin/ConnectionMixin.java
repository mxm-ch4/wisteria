package dev.wisteria.probe.mixin;

import dev.wisteria.probe.Probe;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
abstract class ConnectionMixin {
	@Shadow
	public abstract PacketFlow getReceiving();

	// The integrated server uses the same class; a client-side connection is the one that receives CLIENTBOUND.
	@Inject(
		method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void probe$send(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
		if (this.getReceiving() == PacketFlow.CLIENTBOUND && Probe.onPacketSend(packet)) {
			ci.cancel();
		}
	}

	@Inject(
		method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void probe$receive(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
		if (this.getReceiving() == PacketFlow.CLIENTBOUND && Probe.onPacketReceive(packet)) {
			ci.cancel();
		}
	}
}
