package dev.wisteria.compat.common.mixin;

import dev.wisteria.compat.common.CommonPlatform;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
abstract class LightmapMixin {
	// The game computes brightness from its gamma slider (0..1) here; replace the result when asked to.
	@Inject(method = "extract", at = @At("TAIL"))
	private void wisteria$brightness(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
		CommonPlatform platform = CommonPlatform.instance();
		if (platform != null) {
			float override = platform.commonVisuals().brightness();
			if (override > 0.0F) {
				renderState.brightness = override;
			}
		}
	}
}
