package dev.wisteria.compat.common;

import dev.wisteria.compat.Renderer3D;
import net.minecraft.client.Camera;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/** {@link Renderer3D} on top of the game's gizmos, the same path its own debug renderers use. */
final class GizmoRenderer3D implements Renderer3D {
	private final Vec3 position;
	private final Vector3fc forward;

	GizmoRenderer3D(Camera camera) {
		this.position = camera.position();
		this.forward = camera.forwardVector();
	}

	@Override
	public double cameraX() {
		return this.position.x;
	}

	@Override
	public double cameraY() {
		return this.position.y;
	}

	@Override
	public double cameraZ() {
		return this.position.z;
	}

	@Override
	public float cameraForwardX() {
		return this.forward.x();
	}

	@Override
	public float cameraForwardY() {
		return this.forward.y();
	}

	@Override
	public float cameraForwardZ() {
		return this.forward.z();
	}

	@Override
	public void box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
		int strokeArgb, float strokeWidth, int fillArgb, boolean throughWalls) {
		boolean stroke = (strokeArgb >>> 24) != 0 && strokeWidth > 0.0F;
		boolean fill = (fillArgb >>> 24) != 0;
		if (!stroke && !fill) {
			return;
		}

		GizmoStyle style = stroke && fill
			? GizmoStyle.strokeAndFill(strokeArgb, strokeWidth, fillArgb)
			: stroke ? GizmoStyle.stroke(strokeArgb, strokeWidth) : GizmoStyle.fill(fillArgb);
		GizmoProperties properties = Gizmos.cuboid(new AABB(minX, minY, minZ, maxX, maxY, maxZ), style);
		if (throughWalls) {
			properties.setAlwaysOnTop();
		}
	}

	@Override
	public void line(double x1, double y1, double z1, double x2, double y2, double z2, int argb, float width, boolean throughWalls) {
		if ((argb >>> 24) == 0) {
			return;
		}

		GizmoProperties properties = Gizmos.line(new Vec3(x1, y1, z1), new Vec3(x2, y2, z2), argb, width);
		if (throughWalls) {
			properties.setAlwaysOnTop();
		}
	}
}
