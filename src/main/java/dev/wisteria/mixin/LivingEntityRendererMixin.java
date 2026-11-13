package dev.wisteria.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.wisteria.platform.game.ChamsRenderTypes;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
	@Shadow
	protected EntityModel<?> model;

	// Right after the model is submitted, while the pose stack still holds its pose: the same model again, in chams.
	@SuppressWarnings({"unchecked", "rawtypes"})
	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", shift = At.Shift.AFTER,
			target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
	private void wisteria$chams(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		Integer colour = state.getData(ChamsRenderTypes.COLOR);
		if (colour == null || colour == 0) {
			return;
		}

		boolean rim = Boolean.TRUE.equals(state.getData(ChamsRenderTypes.RIM));
		boolean walls = Boolean.TRUE.equals(state.getData(ChamsRenderTypes.WALLS));
		collector.submitModel((Model)this.model, state, poseStack, ChamsRenderTypes.get(rim, walls), state.lightCoords, OverlayTexture.NO_OVERLAY,
			colour, null, 0, null);
	}
}
