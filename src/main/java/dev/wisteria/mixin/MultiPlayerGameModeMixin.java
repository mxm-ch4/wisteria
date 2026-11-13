package dev.wisteria.mixin;

import dev.wisteria.platform.game.GameEvents;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
	// Remembers which block was used, so the container screen that follows knows where it is.
	@Inject(method = "useItemOn", at = @At("HEAD"))
	private void wisteria$useItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		GameEvents.blockUsed(hit.getBlockPos());
	}
}
