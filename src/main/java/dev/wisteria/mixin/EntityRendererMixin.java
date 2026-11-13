package dev.wisteria.mixin;

import dev.wisteria.Client;
import dev.wisteria.Wisteria;
import dev.wisteria.modules.esp.EntityEsp;
import dev.wisteria.platform.game.ChamsRenderTypes;
import dev.wisteria.platform.game.GameWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
	// ESP chams: decided once per entity per frame, while the game copies the entity into its render state.
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void wisteria$chams(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
		state.setData(ChamsRenderTypes.COLOR, 0);
		Client client = Wisteria.clientOrNull();
		EntityEsp esp = client != null ? client.modules().get(EntityEsp.class) : null;
		if (esp == null || !esp.isEnabled()) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		double distance = minecraft.player != null ? minecraft.player.distanceTo(entity) : 0.0;
		String name = entity instanceof Player ? entity.getScoreboardName() : entity.getName().getString();
		int colour = esp.chamsColor(GameWorld.kindOf(entity), name, entity == minecraft.player, distance);
		if (colour == 0) {
			return;
		}

		if (esp.chamsMode() == EntityEsp.ChamsMode.OUTLINE) {
			state.outlineColor = ARGB.opaque(colour);
		} else {
			state.setData(ChamsRenderTypes.COLOR, colour);
			state.setData(ChamsRenderTypes.RIM, esp.chamsMode() == EntityEsp.ChamsMode.RIM);
			state.setData(ChamsRenderTypes.WALLS, esp.throughWalls());
		}
	}
}
