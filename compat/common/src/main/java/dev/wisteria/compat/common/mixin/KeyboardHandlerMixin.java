package dev.wisteria.compat.common.mixin;

import dev.wisteria.compat.common.CommonPlatform;
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
		CommonPlatform platform = CommonPlatform.instance();
		if (platform != null) {
			platform.commonInput().onNativeKey(action, event);
		}
	}
}
