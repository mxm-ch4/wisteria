package dev.wisteria.mixin;

import dev.wisteria.Wisteria;
import dev.wisteria.event.Events;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
	// Every block change in the client's world passes here, with the old and the new state.
	@Inject(method = "sendBlockUpdated", at = @At("HEAD"))
	private void wisteria$blockUpdated(BlockPos pos, BlockState old, BlockState current, int flags, CallbackInfo ci) {
		if (old != current) {
			Wisteria.post(new Events.BlockUpdate(pos.immutable(), old, current));
		}
	}
}
