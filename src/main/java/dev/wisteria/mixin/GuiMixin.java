package dev.wisteria.mixin;

import dev.wisteria.Wisteria;
import dev.wisteria.event.Events;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
abstract class GuiMixin {
	@Shadow
	private @Nullable Screen screen;

	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void wisteria$setScreen(@Nullable Screen next, CallbackInfo ci) {
		// The game's plain title screen becomes the client's menu, when that is on.
		Screen branded = next != null ? dev.wisteria.platform.game.GameScreens.replaceTitle(next) : null;
		if (branded != null) {
			ci.cancel();
			((Gui)(Object)this).setScreen(branded);
			return;
		}

		if (next != null && next != this.screen && Wisteria.post(new Events.ScreenOpen(next)).isCancelled()) {
			ci.cancel();
			return;
		}

		if (this.screen != null && this.screen != next) {
			Wisteria.post(new Events.ScreenClose(this.screen));
		}
	}
}
