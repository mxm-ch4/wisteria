package dev.wisteria.mixin;

import dev.wisteria.platform.game.GamePlatform;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void wisteria$key(long handle, int action, KeyEvent event, CallbackInfo ci) {
		GamePlatform platform = GamePlatform.instance();
		if (platform != null) {
			platform.gameInput().onNativeKey(action, event);
		}
	}
}
