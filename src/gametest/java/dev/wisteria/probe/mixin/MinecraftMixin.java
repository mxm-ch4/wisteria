package dev.wisteria.probe.mixin;

import dev.wisteria.probe.Probe;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void probe$tick(CallbackInfo ci) {
		Probe.TICKS_MIXIN.incrementAndGet();
	}
}
