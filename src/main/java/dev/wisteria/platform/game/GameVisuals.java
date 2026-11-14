package dev.wisteria.platform.game;

import dev.wisteria.platform.VisualBackend;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

public final class GameVisuals implements VisualBackend {
	private volatile float brightness;
	/** Camera of the last world frame: position and projection x view rotation. Render thread. */
	private @Nullable Vec3 cameraPos;
	private final Matrix4f viewProjection = new Matrix4f();

	@Override
	public void setBrightness(float brightness) {
		this.brightness = Math.max(0.0F, brightness);
	}

	/** Read by the lightmap mixin every time the game rebuilds the lightmap; zero means no override. */
	public float brightness() {
		return this.brightness;
	}

	/** Called once per world frame with the camera the frame was drawn with. */
	void captureCamera(Vec3 position, Matrix4f projection, Matrix4f viewRotation) {
		this.cameraPos = position;
		this.viewProjection.set(projection).mul(viewRotation);
	}

	@Override
	public float @Nullable [] project(double x, double y, double z) {
		Vec3 camera = this.cameraPos;
		if (camera == null) {
			return null;
		}

		Vector4f clip = new Vector4f((float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z), 1.0F);
		this.viewProjection.transform(clip);
		if (clip.w <= 0.05F) {
			return null;
		}

		float ndcX = clip.x / clip.w;
		float ndcY = clip.y / clip.w;
		var window = Minecraft.getInstance().getWindow();
		return new float[] {(ndcX + 1.0F) * 0.5F * window.getGuiScaledWidth(), (1.0F - ndcY) * 0.5F * window.getGuiScaledHeight()};
	}
}
