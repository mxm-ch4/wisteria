package dev.wisteria.gate;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;

/** The parts of the gate that the test API itself spells differently on this version. */
final class GateHooks {
	private GateHooks() {
	}

	static void waitForWorld(TestSingleplayerContext singleplayer) {
		singleplayer.getConnection().waitForChunksRender();
	}

	/** The target the last real frame was rendered into. */
	static RenderTarget mainTarget(Minecraft minecraft) {
		return minecraft.gameRenderer.mainRenderTarget();
	}
}
