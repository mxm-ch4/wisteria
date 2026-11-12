package dev.wisteria.probe.mixin;

import dev.wisteria.probe.Probe;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
	// action: GLFW_RELEASE = 0, GLFW_PRESS = 1, GLFW_REPEAT = 2
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void probe$key(long handle, int action, KeyEvent event, CallbackInfo ci) {
		Probe.onKey(event.key(), event.scancode(), action, event.modifiers());
	}
}
